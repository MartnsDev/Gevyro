package br.com.gestpro.marketplace.webhook;

import br.com.gestpro.caixa.FormaDePagamento;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.marketplace.client.ShopeeApiClient;
import br.com.gestpro.pedidos.CanalVenda;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.security.MessageDigest;

/**
 * Adapter para o webhook da Shopee.
 *
 * Documentação de referência:
 * https://open.shopee.com/documents/v2/v2.push.get_push_config
 *
 * Fluxo:
 *  1. Shopee envia POST com header Authorization contendo HMAC-SHA256
 *     calculado sobre (url_do_push + corpo_bruto) usando o partner_key.
 *  2. Validamos reconstruindo essa mesma string.
 *  3. Se o code == 4 (ORDER_STATUS_UPDATE), buscamos detalhes e criamos o pedido.
 *
 * CORREÇÃO: a versão anterior calculava o HMAC apenas sobre o rawBody,
 * o que nunca bate com o que a Shopee envia. A Shopee assina
 * "url|corpo" -- na prática, concatenados sem separador, mas usando a URL
 * completa do endpoint cadastrada no painel de Live Push da Shopee.
 */
@Service
@RequiredArgsConstructor
public class WebhookShopeeService {

    private static final Logger log = LoggerFactory.getLogger(WebhookShopeeService.class);

    @Value("${gestpro.marketplace.shopee.partner-key:CONFIGURE_ME}")
    private String shopeePartnerKey;

    @Value("${gestpro.marketplace.shopee.partner-id:0}")
    private String shopeePartnerId;

    /**
     * URL PÚBLICA completa e exata cadastrada no painel "Live Push" da Shopee,
     * ex: https://api.gestpro.site/api/v1/webhooks/shopee
     * Precisa ser IDÊNTICA à cadastrada lá (a Shopee usa ela no cálculo do HMAC).
     */
    @Value("${gestpro.marketplace.shopee.webhook-url}")
    private String webhookUrl;

    private final WebhookProcessorService processor;
    private final ShopeeApiClient shopeeApiClient;

    public void processar(byte[] rawBody, String authorization, JsonNode payload) {
        validarAssinatura(rawBody, authorization);

        int code = payload.path("code").asInt(-1);

        // code 4 = novo pedido / atualização de status
        if (code != 4) {
            log.debug("Evento Shopee ignorado (code={})", code);
            return;
        }

        String shopId  = payload.path("shop_id").asText();
        String orderId = payload.path("data").path("ordersn").asText();

        JsonNode detalhes = shopeeApiClient.buscarDetalhePedido(shopId, orderId);

        WebhookOrderDTO order = converterParaOrderDTO(shopId, orderId, detalhes);
        processor.processarPedido(order);
    }


    private void validarAssinatura(byte[] rawBody, String authorization) {
        validarSegredoConfigurado();
        try {
            String bodyString = new String(rawBody, StandardCharsets.UTF_8);
            String baseString = webhookUrl + bodyString;
            String expectedHmac = calcularHmac(baseString.getBytes(StandardCharsets.UTF_8), shopeePartnerKey);

            if (!MessageDigest.isEqual(expectedHmac.getBytes(StandardCharsets.US_ASCII),
                    authorization.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                log.warn("Assinatura Shopee inválida");
                throw new ApiException("Assinatura inválida.", HttpStatus.UNAUTHORIZED, "/webhook/shopee");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao validar assinatura Shopee", e);
            throw new ApiException("Erro na validação da assinatura.", HttpStatus.INTERNAL_SERVER_ERROR, "/webhook/shopee");
        }
    }

    private void validarSegredoConfigurado() {
        if (shopeePartnerKey == null || shopeePartnerKey.isBlank()
                || "CONFIGURE_ME".equals(shopeePartnerKey) || shopeePartnerKey.length() < 16) {
            log.error("Webhook Shopee desativado: segredo de assinatura não configurado com segurança");
            throw new ApiException("Webhook indisponível.", HttpStatus.SERVICE_UNAVAILABLE, "/webhook/shopee");
        }
    }

    private String calcularHmac(byte[] data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }


    private WebhookOrderDTO converterParaOrderDTO(String shopId, String orderId, JsonNode detalhe) {
        List<WebhookOrderDTO.ItemDTO> itens = new ArrayList<>();
        JsonNode itemList = detalhe.path("item_list");

        if (itemList.isArray()) {
            for (JsonNode item : itemList) {
                String anuncioId = item.path("item_id").asText();
                int qty = item.path("model_quantity_purchased").asInt(1);
                itens.add(WebhookOrderDTO.ItemDTO.builder()
                        .anuncioId(anuncioId)
                        .quantidade(qty)
                        .build());
            }
        }

        FormaDePagamento forma = mapearFormaPagamento(
                detalhe.path("payment_method").asText(""));

        BigDecimal frete = detalhe.path("actual_shipping_cost").decimalValue();
        String endereco  = formatarEnderecoShopee(detalhe.path("recipient_address"));

        return WebhookOrderDTO.builder()
                .orderIdExterno(orderId)
                .sellerId(shopId)
                .marketplace(CanalVenda.SHOPEE)
                .formaPagamento(forma)
                .custoFrete(frete)
                .enderecoEntrega(endereco)
                .itens(itens)
                .build();
    }

    private FormaDePagamento mapearFormaPagamento(String paymentMethod) {
        return switch (paymentMethod.toLowerCase()) {
            case "credit_card", "debit_card" -> FormaDePagamento.CARTAO_CREDITO;
            case "bank_transfer"             -> FormaDePagamento.PIX;
            default                          -> FormaDePagamento.OUTRO;
        };
    }

    private String formatarEnderecoShopee(JsonNode addr) {
        if (addr.isMissingNode()) return null;
        return String.format("%s, %s, %s - %s",
                addr.path("full_address").asText(""),
                addr.path("district").asText(""),
                addr.path("city").asText(""),
                addr.path("zipcode").asText(""));
    }
}
