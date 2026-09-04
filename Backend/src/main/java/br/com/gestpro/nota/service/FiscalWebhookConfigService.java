package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.model.FiscalWebhookConfig;
import br.com.gestpro.nota.repository.FiscalWebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class FiscalWebhookConfigService {
    private final FiscalWebhookConfigRepository repository;
    private final FiscalAuthorizationService authorization;
    private final FiscalEncryptionService encryption;
    private final FiscalAuditService audit;
    @Value("${fiscal.webhook.allowed-hosts:}") private String allowedHosts;
    @Value("${fiscal.webhook.dispatch-enabled:false}") private boolean dispatchEnabled;

    @Transactional(readOnly = true)
    public FiscalWebhookConfigResponse buscar(Long empresaId, String ator) {
        authorization.exigir(empresaId, ator, FiscalPermission.CONFIGURAR);
        return repository.findByEmpresaId(empresaId).map(this::response)
                .orElse(new FiscalWebhookConfigResponse(empresaId, false, false, null, Set.of(), null));
    }

    @Transactional
    public FiscalWebhookConfigResponse salvar(Long empresaId, FiscalWebhookConfigRequest request, String ator) {
        authorization.exigir(empresaId, ator, FiscalPermission.CONFIGURAR);
        URI uri = validarUrl(request.url());
        if (request.ativo() && !dispatchEnabled)
            throw new ApiException("O despacho de webhooks não foi liberado neste ambiente.",
                    HttpStatus.PRECONDITION_FAILED, "/api/fiscal/webhook");
        byte[] url = uri.toASCIIString().getBytes(StandardCharsets.UTF_8);
        byte[] segredo = request.segredo().getBytes(StandardCharsets.UTF_8);
        try {
            var encryptedUrl = encryption.encrypt(url);
            var encryptedSecret = encryption.encrypt(segredo);
            FiscalWebhookConfig config = repository.findByEmpresaId(empresaId)
                    .orElseGet(() -> new FiscalWebhookConfig(empresaId));
            String eventos = request.eventos().stream().sorted().map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(","));
            config.atualizar(encryptedUrl.cipherText(), encryptedUrl.nonce(), encryptedSecret.cipherText(),
                    encryptedSecret.nonce(), uri.getHost().toLowerCase(Locale.ROOT), eventos, request.ativo());
            FiscalWebhookConfig salvo = repository.save(config);
            audit.registrar(empresaId, null, "WEBHOOK_FISCAL_CONFIGURADO", ator, "SUCESSO",
                    "ativo=" + request.ativo() + ";eventos=" + eventos);
            return response(salvo);
        } finally { Arrays.fill(url, (byte) 0); Arrays.fill(segredo, (byte) 0); }
    }

    URI validarUrl(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim()).normalize();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || isIpLiteral(host)) throw new IllegalArgumentException();
            Set<String> allowed = Arrays.stream(allowedHosts.split(","))
                    .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!allowed.contains(host.toLowerCase(Locale.ROOT)))
                throw new ApiException("Host do webhook não está na allowlist operacional.",
                        HttpStatus.UNPROCESSABLE_ENTITY, "/api/fiscal/webhook");
            return uri;
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new ApiException("URL de webhook inválida ou insegura.",
                HttpStatus.BAD_REQUEST, "/api/fiscal/webhook"); }
    }
    private boolean isIpLiteral(String host) {
        return host.matches("^[0-9.]+$") || host.contains(":") || "localhost".equalsIgnoreCase(host);
    }
    private FiscalWebhookConfigResponse response(FiscalWebhookConfig c) {
        Set<FiscalWebhookConfigRequest.Evento> eventos = new LinkedHashSet<>();
        for (String value : c.getEventos().split(",")) eventos.add(FiscalWebhookConfigRequest.Evento.valueOf(value));
        return new FiscalWebhookConfigResponse(c.getEmpresaId(), true, c.isAtivo(), c.getHostAprovado(),
                Set.copyOf(eventos), c.getAtualizadoEm());
    }
}
