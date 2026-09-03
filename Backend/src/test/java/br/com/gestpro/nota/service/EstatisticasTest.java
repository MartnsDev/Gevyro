package br.com.gestpro.nota.service;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.nota.service.validacoes.Estatisticas;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstatisticasTest {
    @Test void agregaPendenciasEErrosSemMisturarFaturamento() {
        NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
        when(repository.countByStatus(3L)).thenReturn(List.of(
                new Object[] { 7L, NotaFiscalStatus.AUTORIZADA },
                new Object[] { 2L, NotaFiscalStatus.PENDENTE_EMISSAO },
                new Object[] { 3L, NotaFiscalStatus.VALIDANDO },
                new Object[] { 4L, NotaFiscalStatus.PROCESSANDO },
                new Object[] { 5L, NotaFiscalStatus.ERRO_TECNICO }
        ));
        when(repository.sumValorTotalByEmpresaIdAndStatusAndDataEmissaoBetween(
                eq(3L), eq(NotaFiscalStatus.AUTORIZADA), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("150.25"));

        var resposta = new Estatisticas(repository).calcularEstatisticas(3L);

        assertThat(resposta.getTotalAutorizadas()).isEqualTo(7);
        assertThat(resposta.getTotalAguardando()).isEqualTo(9);
        assertThat(resposta.getTotalErros()).isEqualTo(5);
        assertThat(resposta.getValorTotalMes()).isEqualByComparingTo("150.25");
        verify(repository).countByStatus(3L);
    }
}
