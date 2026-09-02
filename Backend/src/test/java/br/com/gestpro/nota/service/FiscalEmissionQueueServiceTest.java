package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.model.FiscalJob;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.FiscalJobRepository;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.gestpro.infra.security.DistributedRateLimitService;

class FiscalEmissionQueueServiceTest {
    private NotaFiscalRepository notas;
    private FiscalJobRepository jobs;
    private FiscalIdempotencyService idempotency;
    private FiscalAuditService audit;
    private DistributedRateLimitService rateLimit;
    private FiscalEmissionQueueService service;
    private FiscalMetricsService metrics;
    private FiscalFeatureService features;
    private NotaFiscal nota;

    @BeforeEach
    void setup() {
        notas = mock(NotaFiscalRepository.class);
        jobs = mock(FiscalJobRepository.class);
        idempotency = mock(FiscalIdempotencyService.class);
        audit = mock(FiscalAuditService.class);
        rateLimit = mock(DistributedRateLimitService.class);
        metrics = mock(FiscalMetricsService.class);
        features = mock(FiscalFeatureService.class);
        service = new FiscalEmissionQueueService(notas, jobs, idempotency, audit, rateLimit, metrics, features);
        nota = NotaFiscal.builder().id(7L).empresaId(3L).status(NotaFiscalStatus.DIGITACAO).build();
        when(notas.findByIdForUpdate(7L)).thenReturn(Optional.of(nota));
    }

    @Test
    void criaUmJobPersistenteEMarcaNotaComoPendente() {
        when(idempotency.iniciarEmissao(3L, 7L, "chave-123"))
                .thenReturn(new FiscalIdempotencyService.Resultado(11L, true, false, false, false));
        when(jobs.findByIdempotenciaId(11L)).thenReturn(Optional.empty());

        NotaFiscal resultado = service.enfileirar(7L, "chave-123", "dono@empresa.com", "127.0.0.1");

        assertThat(resultado.getStatus()).isEqualTo(NotaFiscalStatus.PENDENTE_EMISSAO);
        ArgumentCaptor<FiscalJob> captor = ArgumentCaptor.forClass(FiscalJob.class);
        verify(jobs).save(captor.capture());
        assertThat(captor.getValue().getDocumentoId()).isEqualTo(7L);
        assertThat(captor.getValue().getIdempotenciaId()).isEqualTo(11L);
        verify(audit).registrar(3L, 7L, "EMISSAO_ENFILEIRADA", "dono@empresa.com", "ACEITA", null);
        verify(metrics).emissaoEnfileirada();
    }

    @Test
    void repeticaoDaMesmaChaveNaoCriaSegundoJob() {
        nota.setStatus(NotaFiscalStatus.PENDENTE_EMISSAO);
        FiscalJob existente = new FiscalJob(3L, 7L, 11L, "dono@empresa.com", "corr", 5);
        when(idempotency.iniciarEmissao(3L, 7L, "chave-123"))
                .thenReturn(new FiscalIdempotencyService.Resultado(11L, false, false, false, false));
        when(jobs.findByIdempotenciaId(11L)).thenReturn(Optional.of(existente));

        assertThat(service.enfileirar(7L, "chave-123", "dono@empresa.com", "127.0.0.1")).isSameAs(nota);
        verify(jobs, never()).save(any());
    }

    @Test
    void resultadoDesconhecidoBloqueiaReenvioCego() {
        when(idempotency.iniciarEmissao(3L, 7L, "chave-123"))
                .thenReturn(new FiscalIdempotencyService.Resultado(11L, false, false, true, false));

        assertThatThrownBy(() -> service.enfileirar(7L, "chave-123", "dono@empresa.com", "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("resultado anterior é desconhecido");
        verifyNoInteractions(jobs);
    }
}
