package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.model.FiscalJob;
import br.com.gestpro.nota.provider.FiscalProvider;
import br.com.gestpro.nota.repository.FiscalJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FiscalJobWorkerTest {
    private FiscalJobCoordinator coordinator;
    private NotaFiscalServiceImpl notas;
    private FiscalAuditService audit;
    private FiscalSituationService situacao;
    private FiscalJobWorker worker;

    @BeforeEach void setup() {
        coordinator = mock(FiscalJobCoordinator.class); notas = mock(NotaFiscalServiceImpl.class);
        audit = mock(FiscalAuditService.class); situacao = mock(FiscalSituationService.class);
        worker = new FiscalJobWorker(mock(FiscalJobRepository.class), coordinator, notas, audit, situacao);
    }

    @Test
    void timeoutDeEmissaoAgendaConsultaSemReenvioCego() {
        var job = claim(FiscalJob.Tipo.EMISSAO, 1);
        when(notas.emitir(7L)).thenThrow(new ApiException("timeout", HttpStatus.BAD_GATEWAY, "/emitir"));

        worker.executar(job);

        verify(coordinator).agendarConsulta(eq(9L), any(Instant.class), eq("ApiException"));
        verify(coordinator, never()).falhar(anyLong(), anyString(), anyBoolean());
        verifyNoInteractions(situacao);
    }

    @Test
    void consultaAutorizadaFinalizaMesmoJob() {
        var job = claim(FiscalJob.Tipo.CONSULTA_SITUACAO, 2);
        var resultado = new FiscalProvider.SituacaoResultado(
                FiscalProvider.SituacaoResultado.Situacao.AUTORIZADA, "100", "Autorizado", "123", "<ret/>");
        when(situacao.consultar(7L)).thenReturn(resultado);

        worker.executar(job);

        verify(coordinator).aplicarConsulta(9L, resultado);
        verifyNoInteractions(notas);
    }

    private FiscalJobCoordinator.JobClaim claim(FiscalJob.Tipo tipo, int tentativa) {
        return new FiscalJobCoordinator.JobClaim(9L, 3L, 7L, 11L, tipo, "ator", "corr", tentativa, 5);
    }
}
