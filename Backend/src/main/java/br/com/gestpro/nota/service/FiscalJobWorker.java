package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.FiscalJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j @Component @RequiredArgsConstructor
public class FiscalJobWorker {
    private final FiscalJobRepository repository;
    private final FiscalJobCoordinator coordinator;
    private final NotaFiscalServiceImpl notaService;
    private final FiscalAuditService auditService;
    private final FiscalSituationService situationService;

    @Scheduled(fixedDelayString = "${fiscal.jobs.poll-delay-ms:1000}")
    public void processar() {
        recuperarJobsInterrompidos();
        for (Long id : repository.findProntos(Instant.now(), PageRequest.of(0, 10))) {
            coordinator.reivindicar(id).ifPresent(this::executar);
        }
    }

    void executar(FiscalJobCoordinator.JobClaim job) {
        try {
            MDC.put("correlationId", job.correlationId());
            if (job.tipo() == br.com.gestpro.nota.model.FiscalJob.Tipo.CONSULTA_SITUACAO) {
                executarConsulta(job);
            } else {
                auditService.registrar(job.empresaId(), job.documentoId(), "EMISSAO_INICIADA", job.ator(), "PROCESSANDO",
                        "tentativa=" + job.tentativa());
                NotaFiscal nota = notaService.emitir(job.documentoId());
                coordinator.concluir(job.id());
                auditService.registrar(job.empresaId(), job.documentoId(), "EMISSAO_FINALIZADA", job.ator(),
                        nota.getStatus().name(), nota.getProtocolo() == null ? null : "protocolo=" + nota.getProtocolo());
            }
        } catch (RuntimeException erro) {
            boolean desconhecido = !(erro instanceof ApiException api) || api.getStatus().is5xxServerError();
            if (desconhecido && job.tentativa() < job.maxTentativas()) {
                coordinator.agendarConsulta(job.id(), proximaTentativa(job.tentativa()), erro.getClass().getSimpleName());
                auditService.registrar(job.empresaId(), job.documentoId(), "CONSULTA_SITUACAO_AGENDADA", job.ator(),
                        "AGUARDANDO", "tentativa=" + job.tentativa());
                return;
            }
            coordinator.falhar(job.id(), erro.getClass().getSimpleName(), desconhecido);
            auditService.registrar(job.empresaId(), job.documentoId(), "EMISSAO_FALHOU", job.ator(),
                    desconhecido ? "RESULTADO_DESCONHECIDO" : "FALHA_VALIDACAO", erro.getClass().getSimpleName());
            log.warn("Job fiscal {} terminou com falha segura: {}", job.id(), erro.getClass().getSimpleName());
        } finally { MDC.remove("correlationId"); }
    }

    private void executarConsulta(FiscalJobCoordinator.JobClaim job) {
        var resultado = situationService.consultar(job.documentoId());
        if (resultado.situacao() == br.com.gestpro.nota.provider.FiscalProvider.SituacaoResultado.Situacao.DESCONHECIDA)
            throw new IllegalStateException("Consulta fiscal inconclusiva.");
        coordinator.aplicarConsulta(job.id(), resultado);
        auditService.registrar(job.empresaId(), job.documentoId(), "SITUACAO_FISCAL_CONSULTADA", job.ator(),
                resultado.situacao().name(), "codigo=" + resultado.codigo());
    }

    private Instant proximaTentativa(int tentativa) {
        long base = Math.min(300, 5L * (1L << Math.min(tentativa - 1, 6)));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, base / 3 + 1));
        return Instant.now().plusSeconds(base + jitter);
    }

    private void recuperarJobsInterrompidos() {
        Instant limite = Instant.now().minus(Duration.ofMinutes(10));
        for (Long id : repository.findTravados(limite, PageRequest.of(0, 10))) {
            coordinator.recuperarInterrompido(id).ifPresent(job -> {
                auditService.registrar(job.empresaId(), job.documentoId(), "EMISSAO_RECUPERADA", job.ator(),
                        job.autorizado() ? "AUTORIZADA" : "AGUARDANDO_CONSULTA",
                        job.autorizado() ? "sucesso confirmado na base local" : "consulta fiscal agendada");
            });
        }
    }
}
