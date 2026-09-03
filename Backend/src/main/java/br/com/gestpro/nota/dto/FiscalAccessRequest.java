package br.com.gestpro.nota.dto;

import br.com.gestpro.nota.FiscalRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FiscalAccessRequest(
        @NotNull @Email @Size(max = 254) String email,
        @NotNull FiscalRole role) {}
