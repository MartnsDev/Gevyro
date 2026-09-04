package br.com.gestpro.nota.dto;
import java.time.Instant;
import java.util.Set;
public record FiscalWebhookConfigResponse(Long empresaId, boolean configurado, boolean ativo,
        String hostAprovado, Set<FiscalWebhookConfigRequest.Evento> eventos, Instant atualizadoEm) {}
