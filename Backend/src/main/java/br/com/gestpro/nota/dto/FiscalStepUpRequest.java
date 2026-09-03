package br.com.gestpro.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FiscalStepUpRequest(@NotBlank @Size(max = 200) String senha) {}
