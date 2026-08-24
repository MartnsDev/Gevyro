package br.com.gestpro.pedidos.dto;

import br.com.gestpro.caixa.FormaDePagamento;
import br.com.gestpro.pedidos.CanalVenda;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RegistrarPedidoDTO {

    String emailUsuario;
    Long empresaId;

    Long idCliente;

    @NotEmpty(message = "A lista de itens não pode estar vazia")
    @Valid
    List<ItemPedidoDTO> itens;

    @NotNull(message = "Forma de pagamento é obrigatória")
    FormaDePagamento formaPagamento;

    CanalVenda canalVenda; // null → OUTRO

    @Size(max = 100, message = "Conta de destino deve ter no máximo 100 caracteres")
    String contaDestino; // Ex.: "Mercado Pago", "Nubank"

    @Size(max = 300, message = "Endereço deve ter no máximo 300 caracteres")
    String enderecoEntrega;

    @PositiveOrZero(message = "Frete não pode ser negativo")
    BigDecimal custoFrete;

    @PositiveOrZero(message = "Desconto não pode ser negativo")
    BigDecimal desconto;

    @Size(max = 500, message = "Observação deve ter no máximo 500 caracteres")
    String observacao;

    @Data
    public static class ItemPedidoDTO {
        @NotNull(message = "Produto é obrigatório")
        Long idProduto;

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade mínima é 1")
        Integer quantidade;
    }
}
