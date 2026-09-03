package br.com.gestpro.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContingenciaOfflineRequest(
        @NotBlank @Size(min = 15, max = 255) String justificativa
) {}
