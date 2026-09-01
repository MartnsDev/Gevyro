package br.com.gestpro.nota.service;

import br.com.gestpro.nota.model.FiscalJob;
import br.com.gestpro.nota.repository.FiscalJobRepository;
import br.com.gestpro.nota.repository.FiscalIdempotencyRepository;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.nota.NotaFiscalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.time.Instant;
import br.com.gestpro.nota.provider.FiscalProvider;

@Service @RequiredArgsConstructor
public class FiscalJobCoordinator {
    private final FiscalJobRepository repository;
    private final FiscalIdempotencyRepository idempotencyRepository;
    private final NotaFiscalRepository notaRepository;
    private final FiscalXmlService fiscalXmlService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<JobClaim> reivindicar(Long id) {
        FiscalJob job = repository.bloquear(id).orElse(null);
        if (job == null || job.getStatus() != FiscalJob.Status.PENDENTE) return Optional.empty();
        job.iniciar();
        return Optional.of(new JobClaim(job.getId(), job.getEmpresaId(), job.getDocumentoId(), job.getIdempotenciaId(), job.getTipo(),
                job.getAtor(), job.getCorrelationId(), job.getTentativas(), job.getMaxTentativas()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluir(Long id) {
        FiscalJob job = repository.bloquear(id).orElseThrow();
        job.concluir();
        idempotencyRepository.findById(job.getIdempotenciaId()).orElseThrow().concluir();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(Long id, String erro, boolean desconhecido) {
        FiscalJob job = repository.bloquear(id).orElseThrow();
        job.falhar(erro, desconhecido);
        var idempotencia = idempotencyRepository.findById(job.getIdempotenciaId()).orElseThrow();
        if (desconhecido) idempotencia.resultadoDesconhecido(); else idempotencia.falhar();
        notaRepository.findByIdForUpdate(job.getDocumentoId()).ifPresent(nota -> {
            if (nota.getStatus() != NotaFiscalStatus.AUTORIZADA) {
                nota.setStatus(NotaFiscalStatus.ERRO_TECNICO);
                nota.setMotivoRejeicao(desconhecido
                        ? "Resultado externo desconhecido. Consulte a situação fiscal antes de reenviar."
                        : "A emissão não passou pelas validações necessárias.");
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void agendarConsulta(Long id, Instant quando, String erro) {
        repository.bloquear(id).orElseThrow().agendarConsulta(quando, erro);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aplicarConsulta(Long id, FiscalProvider.SituacaoResultado resultado) {
        FiscalJob job = repository.bloquear(id).orElseThrow();
        var nota = notaRepository.findByIdForUpdate(job.getDocumentoId()).orElseThrow();
        switch (resultado.situacao()) {
            case AUTORIZADA -> {
                String nfeProc = fiscalXmlService.montarNfeProc(nota.getXmlEnviado(), resultado.xmlRetorno());
                fiscalXmlService.armazenarAutorizado(nota.getEmpresaId(), nota.getId(), nfeProc);
                nota.setStatus(NotaFiscalStatus.AUTORIZADA); nota.setProtocolo(resultado.protocolo());
                nota.setXmlRetorno(resultado.xmlRetorno()); nota.setDataAutorizacao(java.time.LocalDateTime.now());
                nota.setMotivoRejeicao(null); job.concluir();
                idempotencyRepository.findById(job.getIdempotenciaId()).orElseThrow().concluir();
            }
            case CANCELADA -> {
                nota.setStatus(NotaFiscalStatus.CANCELADA); nota.setXmlRetorno(resultado.xmlRetorno()); job.concluir();
                idempotencyRepository.findById(job.getIdempotenciaId()).orElseThrow().concluir();
            }
            case REJEITADA, NAO_ENCONTRADA -> {
                nota.setStatus(NotaFiscalStatus.REJEITADA); nota.setXmlRetorno(resultado.xmlRetorno());
                nota.setMotivoRejeicao("[" + resultado.codigo() + "] " + resultado.motivo());
                job.falhar("Consulta conclusiva: " + resultado.codigo(), false);
                idempotencyRepository.findById(job.getIdempotenciaId()).orElseThrow().falhar();
            }
            case DESCONHECIDA -> throw new IllegalStateException("Situação fiscal ainda inconclusiva.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<JobRecovery> recuperarInterrompido(Long id) {
        FiscalJob job = repository.bloquear(id).orElse(null);
        if (job == null || job.getStatus() != FiscalJob.Status.PROCESSANDO) return Optional.empty();
        boolean autorizado = notaRepository.findById(job.getDocumentoId())
                .map(n -> n.getStatus() == NotaFiscalStatus.AUTORIZADA).orElse(false);
        if (autorizado) {
            job.concluir();
            idempotencyRepository.findById(job.getIdempotenciaId()).orElseThrow().concluir();
        } else {
            job.agendarConsulta(Instant.now(), "Worker interrompido sem confirmação do resultado externo.");
        }
        return Optional.of(new JobRecovery(job.getEmpresaId(), job.getDocumentoId(), job.getAtor(), autorizado));
    }

    public record JobClaim(Long id, Long empresaId, Long documentoId, Long idempotenciaId, FiscalJob.Tipo tipo, String ator,
                           String correlationId, int tentativa, int maxTentativas) {}
    public record JobRecovery(Long empresaId, Long documentoId, String ator, boolean autorizado) {}
}
