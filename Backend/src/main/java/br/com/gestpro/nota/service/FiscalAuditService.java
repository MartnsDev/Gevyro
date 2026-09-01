package br.com.gestpro.nota.service;

import br.com.gestpro.nota.config.NotaFiscalConfig;
import br.com.gestpro.nota.model.FiscalAuditLog;
import br.com.gestpro.nota.repository.FiscalAuditStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service @RequiredArgsConstructor
public class FiscalAuditService {
    private static final String GENESIS = "0".repeat(64);
    private final FiscalAuditStore store;
    private final NotaFiscalConfig.NotaFiscalProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long empresaId, Long documentoId, String acao, String ator,
                          String resultado, String detalhes) {
        store.bloquearEmpresa(empresaId);
        String anterior = store.ultimo(empresaId).map(FiscalAuditLog::getHashRegistro).orElse(GENESIS);
        Instant agora = Instant.now();
        String correlation = MDC.get("correlationId");
        if (correlation == null) correlation = "system-" + agora.toEpochMilli();
        String ambiente = properties.isHomologacao() ? "HOMOLOGACAO" : "PRODUCAO";
        String detalhesLimpos = detalhes == null ? null : detalhes.replaceAll("[\\r\\n]", " ");
        String detalhesSeguros = detalhesLimpos == null ? null : detalhesLimpos.substring(0, Math.min(1000, detalhesLimpos.length()));
        String canonical = String.join("|", anterior, String.valueOf(empresaId), String.valueOf(documentoId),
                acao, ator, ambiente, resultado, String.valueOf(detalhesSeguros), correlation, agora.toString());
        store.adicionar(new FiscalAuditLog(empresaId, documentoId, acao, ator, ambiente, resultado,
                detalhesSeguros, correlation, anterior, sha256(canonical), agora));
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 indisponível.", impossible); }
    }
}
