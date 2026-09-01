package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.model.FiscalIdempotency;
import br.com.gestpro.nota.repository.FiscalIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class FiscalIdempotencyService {
    public static final String EMISSAO = "EMISSAO";
    private final FiscalIdempotencyRepository repository;
    private final FiscalIdempotencyWriter writer;

    public Resultado iniciarEmissao(Long empresaId, Long documentoId, String rawKey) {
        String key = validar(rawKey);
        boolean criadaAgora = false;
        FiscalIdempotency existente = repository
                .findByEmpresaIdAndOperacaoAndIdempotencyKey(empresaId, EMISSAO, key).orElse(null);
        if (existente == null) {
            try { writer.iniciar(empresaId, documentoId, EMISSAO, key); criadaAgora = true; }
            catch (DataIntegrityViolationException concorrencia) { /* outra instância reservou */ }
            existente = repository.findByEmpresaIdAndOperacaoAndIdempotencyKey(empresaId, EMISSAO, key)
                    .orElseThrow(() -> new IllegalStateException("Falha ao persistir idempotência fiscal."));
        }
        if (!existente.getDocumentoId().equals(documentoId)) {
            throw new ApiException("Idempotency-Key já utilizada em outra nota.", HttpStatus.CONFLICT, "/api/nota-fiscal/emitir");
        }
        return new Resultado(existente.getId(), criadaAgora, existente.getStatus() == FiscalIdempotency.Status.CONCLUIDA,
                existente.getStatus() == FiscalIdempotency.Status.RESULTADO_DESCONHECIDO,
                existente.getStatus() == FiscalIdempotency.Status.FALHOU);
    }

    public void concluir(Long id) { writer.concluir(id); }
    public void falhar(Long id) { writer.falhar(id); }
    public void resultadoDesconhecido(Long id) { writer.resultadoDesconhecido(id); }

    private String validar(String key) {
        if (key == null || key.isBlank()) throw new ApiException("Idempotency-Key é obrigatória para emissão fiscal.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/emitir");
        String normalized = key.trim();
        if (normalized.length() < 8 || normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+"))
            throw new ApiException("Idempotency-Key inválida.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/emitir");
        return normalized;
    }

    public record Resultado(Long operacaoId, boolean nova, boolean concluida, boolean resultadoDesconhecido, boolean falhou) {}
}
