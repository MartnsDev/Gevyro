package br.com.gestpro.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelarNotaRequest {
    @NotNull
    private Long notaId;

    @NotBlank
    @Size(min = 15, max = 255)
    private String justificativa;
}
