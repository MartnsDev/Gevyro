package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.model.*;
import br.com.gestpro.nota.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import br.com.gestpro.infra.security.DistributedRateLimitService;

@Service @RequiredArgsConstructor
public class FiscalEmissionQueueService {
    private final NotaFiscalRepository notaRepository;
    private final FiscalJobRepository jobRepository;
    private final FiscalIdempotencyService idempotencyService;
    private final FiscalAuditService auditService;
    private final DistributedRateLimitService rateLimit;
    private final FiscalMetricsService metrics;
    private final FiscalFeatureService features;

    @Transactional
    public NotaFiscal enfileirar(Long notaId, String key, String ator, String ip) {
        NotaFiscal nota = notaRepository.findByIdForUpdate(notaId)
                .orElseThrow(() -> new ApiException("Nota fiscal não encontrada.", HttpStatus.NOT_FOUND, "/api/nota-fiscal/emitir"));
        features.validarEmissaoHabilitada(nota.getEmpresaId(), nota.getTipo());
        rateLimit.verificar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, nota.getEmpresaId(), ator, ip,
                "/api/nota-fiscal/emitir");
        FiscalIdempotencyService.Resultado op = idempotencyService.iniciarEmissao(nota.getEmpresaId(), notaId, key);
        if (op.concluida()) return nota;
        if (op.resultadoDesconhecido()) throw new ApiException(
                "O resultado anterior é desconhecido. Consulte a situação fiscal antes de uma nova emissão.",
                HttpStatus.CONFLICT, "/api/nota-fiscal/emitir");
        if (op.falhou()) throw new ApiException(
                "A tentativa vinculada a esta Idempotency-Key falhou. Corrija a nota e use uma nova chave.",
                HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/emitir");
        FiscalJob existente = jobRepository.findByIdempotenciaId(op.operacaoId()).orElse(null);
        if (existente != null) return nota;
        if (!op.nova()) throw new ApiException("Esta emissão já está em processamento.", HttpStatus.CONFLICT, "/api/nota-fiscal/emitir");
        if (nota.getStatus() != NotaFiscalStatus.DIGITACAO && nota.getStatus() != NotaFiscalStatus.REJEITADA
                && nota.getStatus() != NotaFiscalStatus.ERRO_TECNICO)
            throw new ApiException("O estado atual da nota não permite emissão.", HttpStatus.CONFLICT, "/api/nota-fiscal/emitir");
        String correlation = MDC.get("correlationId");
        jobRepository.save(new FiscalJob(nota.getEmpresaId(), notaId, op.operacaoId(), ator,
                correlation == null ? UUID.randomUUID().toString() : correlation, 5));
        nota.setStatus(NotaFiscalStatus.PENDENTE_EMISSAO);
        metrics.emissaoEnfileirada();
        auditService.registrar(nota.getEmpresaId(), notaId, "EMISSAO_ENFILEIRADA", ator, "ACEITA", null);
        return nota;
    }
}
