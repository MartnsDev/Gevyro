package br.com.gestpro.marketplace.webhook;

import br.com.gestpro.caixa.FormaDePagamento;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.marketplace.client.MercadoLivreApiClient;
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
import java.time.Instant;

/**
 * Adapter para o webhook do Mercado Livre.
 *
 * Documentação de referência:
 * https://developers.mercadolivre.com.br/pt_br/notificacoes-de-transacoes
 *
 * Fluxo:
 *  1. ML envia POST com header x-signature contendo ts e hash HMAC-SHA256
 *  2. Validamos reconstruindo a string "id:[id];request-id:[rid];ts:[ts]"
 *  3. Se topic == "orders_v2", buscamos detalhes e criamos o pedido
 */
@Service
@RequiredArgsConstructor
public class WebhookMercadoLivreService {

    private static final Logger log = LoggerFactory.getLogger(WebhookMercadoLivreService.class);

    @Value("${gestpro.marketplace.mercadolivre.secret-key:CONFIGURE_ME}")
    private String mlSecretKey;

    private final WebhookProcessorService processor;
    private final MercadoLivreApiClient mlApiClient; // declarado abaixo

    /**
     * Ponto de entrada chamado pelo WebhookController.
     *
     * @param xSignature   header x-signature enviado pelo ML
     * @param xRequestId   header x-request-id enviado pelo ML
     * @param payload      corpo deserializado
     */
    public void processar(String xSignature, String xRequestId, JsonNode payload) {
        String topic    = payload.path("topic").asText("");
        String resource = payload.path("resource").asText("");

        // Valida a assinatura usando o id do recurso + request-id + timestamp do header
        validarAssinatura(xSignature, xRequestId, extrairId(resource));

        // Só processamos notificações de pedidos
        if (!topic.equals("orders_v2")) {
            log.debug("Evento ML ignorado (topic={})", topic);
            return;
        }

        String orderId  = extrairId(resource); // ex: "/orders/1234567890" → "1234567890"
        String sellerId = payload.path("user_id").asText();

        // Busca detalhes completos via API do ML
        JsonNode detalhes = mlApiClient.buscarDetalhePedido(orderId, sellerId);

        WebhookOrderDTO order = converterParaOrderDTO(sellerId, orderId, detalhes);
        processor.processarPedido(order);
    }


    /**
     * ML envia: x-signature: ts=1704067200,v1=abc123...
     * Reconstruímos a mensagem "id:[orderId];request-id:[xRequestId];ts:[ts]"
     * e comparamos o HMAC-SHA256 com v1.
     */
    private void validarAssinatura(String xSignature, String xRequestId, String orderId) {
        try {
            String ts = extrairCampoSignature(xSignature, "ts");
            String v1 = extrairCampoSignature(xSignature, "v1");
            validarTimestamp(ts);

            String mensagem = "id:" + orderId + ";request-id:" + xRequestId + ";ts:" + ts;
            String hmacCalculado = calcularHmac(mensagem, mlSecretKey);

            if (!MessageDigest.isEqual(hmacCalculado.getBytes(StandardCharsets.US_ASCII),
                    v1.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                log.warn("Assinatura ML inválida");
                throw new ApiException("Assinatura inválida.", HttpStatus.UNAUTHORIZED, "/webhook/mercadolivre");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao validar assinatura ML", e);
            throw new ApiException("Erro na validação da assinatura.", HttpStatus.INTERNAL_SERVER_ERROR, "/webhook/mercadolivre");
        }
    }

    private void validarTimestamp(String valor) {
        try {
            long timestamp = Long.parseLong(valor);
            if (timestamp > 10_000_000_000L) timestamp /= 1000;
            long diferenca = Math.abs(Instant.now().getEpochSecond() - timestamp);
            if (diferenca > 300) throw new ApiException("Assinatura expirada.",
                    HttpStatus.UNAUTHORIZED, "/webhook/mercadolivre");
        } catch (NumberFormatException exception) {
            throw new ApiException("Timestamp inválido.", HttpStatus.BAD_REQUEST, "/webhook/mercadolivre");
        }
    }

    private String extrairCampoSignature(String xSignature, String campo) {
        for (String part : xSignature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(campo)) return kv[1];
        }
        throw new ApiException("Campo ausente na assinatura: " + campo,
                HttpStatus.BAD_REQUEST, "/webhook/mercadolivre");
    }

    private String calcularHmac(String mensagem, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8)));
    }


    private WebhookOrderDTO converterParaOrderDTO(String sellerId, String orderId, JsonNode detalhe) {
        List<WebhookOrderDTO.ItemDTO> itens = new ArrayList<>();
        JsonNode orderItems = detalhe.path("order_items");

        if (orderItems.isArray()) {
            for (JsonNode item : orderItems) {
                // No ML, o ID do anúncio fica em item.item.id
                String anuncioId = item.path("item").path("id").asText();
                int qty = item.path("quantity").asInt(1);
                itens.add(WebhookOrderDTO.ItemDTO.builder()
                        .anuncioId(anuncioId)
                        .quantidade(qty)
                        .build());
            }
        }

        FormaDePagamento forma = mapearFormaPagamento(
                detalhe.path("payments").path(0).path("payment_type").asText(""));

        // ML retorna frete em shipping.cost
        BigDecimal frete = detalhe.path("shipping").path("cost").decimalValue();
        String endereco  = formatarEnderecoML(detalhe.path("shipping").path("receiver_address"));

        return WebhookOrderDTO.builder()
                .orderIdExterno(orderId)
                .sellerId(sellerId)
                .marketplace(CanalVenda.MERCADO_LIVRE)
                .formaPagamento(forma)
                .custoFrete(frete)
                .enderecoEntrega(endereco)
                .itens(itens)
                .build();
    }

    private FormaDePagamento mapearFormaPagamento(String type) {
        return switch (type) {
            case "credit_card"  -> FormaDePagamento.CARTAO_CREDITO;
            case "debit_card"   -> FormaDePagamento.CARTAO_DEBITO;
            case "account_money"-> FormaDePagamento.PIX;
            default             -> FormaDePagamento.OUTRO;
        };
    }

    private String formatarEnderecoML(JsonNode addr) {
        if (addr.isMissingNode()) return null;
        return String.format("%s %s, %s - %s/%s",
                addr.path("street_name").asText(""),
                addr.path("street_number").asText(""),
                addr.path("city").path("name").asText(""),
                addr.path("state").path("name").asText(""),
                addr.path("zip_code").asText(""));
    }

    private String extrairId(String resource) {
        // "/orders/1234567890" → "1234567890"
        String[] parts = resource.split("/");
        return parts[parts.length - 1];
    }
}
