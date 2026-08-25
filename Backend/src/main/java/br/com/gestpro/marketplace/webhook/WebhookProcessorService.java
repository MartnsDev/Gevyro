package br.com.gestpro.marketplace.webhook;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.marketplace.model.MarketplaceConnection;
import br.com.gestpro.marketplace.model.MarketplaceProductLink;
import br.com.gestpro.marketplace.repository.MarketplaceConnectionRepository;
import br.com.gestpro.marketplace.repository.MarketplaceProductLinkRepository;
import br.com.gestpro.pedidos.CanalVenda;
import br.com.gestpro.pedidos.dto.RegistrarPedidoDTO;
import br.com.gestpro.pedidos.model.Pedido;
import br.com.gestpro.pedidos.service.PedidoServiceInterface;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.time.Duration;

/**
 * Serviço central que recebe os dados normalizados de um webhook
 * (independente de qual marketplace veio) e cria o pedido no GestPro.
 *
 * Cada serviço de webhook (Shopee, ML) é responsável por:
 *  1. Validar a assinatura da chamada
 *  2. Buscar os detalhes completos do pedido via API do marketplace
 *  3. Converter para WebhookOrderDTO
 *  4. Chamar processarPedido() aqui
 */
@Service
@RequiredArgsConstructor
public class WebhookProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessorService.class);

    private final MarketplaceConnectionRepository connectionRepository;
    private final MarketplaceProductLinkRepository linkRepository;
    private final PedidoServiceInterface pedidoService;
    private final StringRedisTemplate redis;

    /**
     * Dado um pedido normalizado vindo de qualquer marketplace,
     * cria o pedido no GestPro reutilizando exatamente o mesmo
     * PedidoServiceImpl já existente.
     */
    public Pedido processarPedido(WebhookOrderDTO order) {
        String idempotenciaKey = "webhook:pedido:" + order.getMarketplace().name()
                + ":" + order.getSellerId() + ":" + order.getOrderIdExterno();
        Boolean novoEvento = redis.opsForValue().setIfAbsent(idempotenciaKey, "processing", Duration.ofDays(30));
        if (!Boolean.TRUE.equals(novoEvento)) {
            log.info("Webhook duplicado ignorado: marketplace={} orderExterno={}",
                    order.getMarketplace(), order.getOrderIdExterno());
            return null;
        }
        try {
        // 1. Identificar a empresa pelo sellerId
        MarketplaceConnection conn = connectionRepository
                .findBySellerIdAndMarketplaceAndActiveTrue(order.getSellerId(), order.getMarketplace())
                .orElseThrow(() -> {
                    log.warn("Webhook recebido para sellerId={} marketplace={} sem conexão ativa.",
                            order.getSellerId(), order.getMarketplace());
                    return new ApiException(
                            "Nenhuma empresa conectada para este seller.", HttpStatus.NOT_FOUND, "/webhook");
                });

        // 2. Mapear cada item do marketplace para um produto interno
        List<RegistrarPedidoDTO.ItemPedidoDTO> itens = order.getItens().stream()
                .map(item -> {
                    MarketplaceProductLink link = linkRepository
                            .findByMarketplaceAndAnuncioId(order.getMarketplace(), item.getAnuncioId())
                            .orElseThrow(() -> {
                                log.warn("Anúncio não vinculado: marketplace={} anuncioId={}",
                                        order.getMarketplace(), item.getAnuncioId());
                                return new ApiException(
                                        "Anúncio " + item.getAnuncioId() + " não vinculado a nenhum produto.",
                                        HttpStatus.UNPROCESSABLE_ENTITY, "/webhook");
                            });

                    RegistrarPedidoDTO.ItemPedidoDTO dto = new RegistrarPedidoDTO.ItemPedidoDTO();
                    dto.setIdProduto(link.getProduto().getId());
                    dto.setQuantidade(item.getQuantidade());
                    return dto;
                })
                .toList();

        // 3. Montar o RegistrarPedidoDTO com os dados do dono da empresa
        String emailDono = conn.getEmpresa().getDono().getEmail();

        RegistrarPedidoDTO dto = new RegistrarPedidoDTO();
        dto.setEmailUsuario(emailDono);
        dto.setEmpresaId(conn.getEmpresa().getId());
        dto.setItens(itens);
        dto.setCanalVenda(order.getMarketplace());
        dto.setFormaPagamento(order.getFormaPagamento());
        dto.setCustoFrete(order.getCustoFrete() != null ? order.getCustoFrete() : BigDecimal.ZERO);
        dto.setDesconto(BigDecimal.ZERO);
        dto.setEnderecoEntrega(order.getEnderecoEntrega());
        dto.setObservacao("Pedido automático #" + order.getOrderIdExterno()
                + " via " + order.getMarketplace().name());

        // 4. Delegar ao PedidoServiceImpl — ele debita estoque, calcula totais, persiste
        Pedido pedido = pedidoService.registrarPedido(dto);

        log.info("Pedido automático criado: id={} empresa={} marketplace={} orderExterno={}",
                pedido.getId(), conn.getEmpresa().getNomeFantasia(),
                order.getMarketplace(), order.getOrderIdExterno());

            redis.opsForValue().set(idempotenciaKey, "processed", Duration.ofDays(30));
            return pedido;
        } catch (RuntimeException exception) {
            redis.delete(idempotenciaKey);
            throw exception;
        }
    }
}
