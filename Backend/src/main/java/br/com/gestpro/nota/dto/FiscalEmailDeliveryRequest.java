package br.com.gestpro.nota.dto;
import jakarta.validation.constraints.*;
public record FiscalEmailDeliveryRequest(
        @NotBlank @Email @Size(max = 254) String destinatario,
        @AssertTrue(message = "Confirme explicitamente o destinatário fiscal.") boolean confirmarDestinatario
) {}
