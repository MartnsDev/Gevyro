package br.com.gestpro.nota.dto;
import jakarta.validation.constraints.*;
import java.util.Set;
public record FiscalWebhookConfigRequest(
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Size(min = 32, max = 200) String segredo,
        @NotEmpty @Size(max = 3) Set<Evento> eventos,
        boolean ativo
) {
    public enum Evento { DOCUMENTO_AUTORIZADO, DOCUMENTO_REJEITADO, DOCUMENTO_CANCELADO }
}
