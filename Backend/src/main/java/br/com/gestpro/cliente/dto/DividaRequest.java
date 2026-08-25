package br.com.gestpro.cliente.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;

@Data
public class DividaRequest {
    @NotNull
    private Long clienteId;
    @NotNull
    private Long empresaId;
    @NotBlank @Size(max = 255)
    private String descricao;
    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal valor;
    private LocalDate vencimento;
}
