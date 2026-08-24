package br.com.gestpro.venda.dto;

import br.com.gestpro.caixa.FormaDePagamento;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarVendaDTO {

    private String emailUsuario;

    @NotNull(message = "ID do caixa é obrigatório")
    private Long idCaixa;

    private Long idCliente;

    @NotEmpty(message = "Lista de itens não pode ser vazia")
    @Valid
    private List<ItemVendaDTO> itens;

    // Pagamento principal (obrigatório)
    @NotNull(message = "Forma de pagamento é obrigatória")
    private FormaDePagamento formaPagamento;

    // Pagamento misto: segunda forma + valor
    private FormaDePagamento formaPagamento2;
    private BigDecimal valorPagamento2; // quanto foi pago na segunda forma

    // Valor recebido (para calcular troco no dinheiro)
    private BigDecimal valorRecebido;

    @PositiveOrZero(message = "Desconto não pode ser negativo")
    private BigDecimal desconto;

    @Size(max = 500, message = "Observação deve ter no máximo 500 caracteres")
    private String observacao;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemVendaDTO {
        @NotNull(message = "Produto é obrigatório")
        private Long idProduto;

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade mínima é 1")
        private Integer quantidade;
    }
}
