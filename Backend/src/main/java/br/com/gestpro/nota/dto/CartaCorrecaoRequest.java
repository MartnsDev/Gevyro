package br.com.gestpro.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CartaCorrecaoRequest {
    @NotNull
    private Long notaId;
    @NotBlank
    @Size(min = 15, max = 1000)
    private String correcao;
}
