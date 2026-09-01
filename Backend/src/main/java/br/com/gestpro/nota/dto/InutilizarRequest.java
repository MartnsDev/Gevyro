package br.com.gestpro.nota.dto;

import br.com.gestpro.nota.TipoNota;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InutilizarRequest {
    @NotNull
    private Long empresaId;
    @NotNull
    private TipoNota tipo;
    @NotBlank @Pattern(regexp = "[0-9]{1,3}")
    private String serie;
    @NotNull @Min(2006)
    private Integer ano;
    @NotNull @Min(1) @Max(999999999)
    private Long numeroInicio;
    @NotNull @Min(1) @Max(999999999)
    private Long numeroFim;
    @NotBlank @Size(min = 15, max = 255)
    private String justificativa;
}
