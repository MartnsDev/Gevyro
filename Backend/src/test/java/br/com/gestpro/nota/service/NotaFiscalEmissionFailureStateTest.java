package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotaFiscalEmissionFailureStateTest {
    @Test void falhaAntesDoEnvioVoltaParaDigitacao() {
        NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
        NotaFiscalServiceImpl service = mock(NotaFiscalServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "notaFiscalRepository", repository);
        NotaFiscal nota = NotaFiscal.builder().status(NotaFiscalStatus.VALIDANDO).build();
        service.registrarFalhaEmissao(nota, false);
        assertThat(nota.getStatus()).isEqualTo(NotaFiscalStatus.DIGITACAO);
        assertThat(nota.getMotivoRejeicao()).contains("validação local");
        verify(repository).save(nota);
    }

    @Test void falhaDepoisDoEnvioExigeConsultaAntesDeReenvio() {
        NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
        NotaFiscalServiceImpl service = mock(NotaFiscalServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "notaFiscalRepository", repository);
        NotaFiscal nota = NotaFiscal.builder().status(NotaFiscalStatus.PROCESSANDO).build();
        service.registrarFalhaEmissao(nota, true);
        assertThat(nota.getStatus()).isEqualTo(NotaFiscalStatus.ERRO_TECNICO);
        assertThat(nota.getMotivoRejeicao()).contains("Consulte a situação antes de reenviar");
    }

    @Test void apiExceptionNaoRevertePersistenciaDoEstadoSeguro() throws Exception {
        Method emitir = NotaFiscalServiceImpl.class.getMethod("emitir", Long.class);
        Transactional transactional = emitir.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.noRollbackFor()).contains(ApiException.class);
    }
}
