package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.dto.FilterNotaFiscalDTO;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.nota.service.validacoes.Listar;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListarNotaFiscalTest {
    @Test void aplicaPaginacaoBaseUmEFiltrosNoBanco() {
        NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
        when(repository.findWithFilters(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(1, 50)));
        FilterNotaFiscalDTO filtro = FilterNotaFiscalDTO.builder().empresaId(3L).page(2).limit(50)
                .numero(9L).serie("1").valorMin(new BigDecimal("10.00")).valorMax(new BigDecimal("20.00")).build();

        new Listar(repository).listar(filtro);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findWithFilters(eq(3L), isNull(), isNull(), isNull(), eq(9L), eq("1"),
                eq(new BigDecimal("10.00")), eq(new BigDecimal("20.00")), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test void rejeitaLimiteEDatasInvalidas() {
        Listar service = new Listar(mock(NotaFiscalRepository.class));
        assertThatThrownBy(() -> service.listar(FilterNotaFiscalDTO.builder().empresaId(3L).limit(101).build()))
                .isInstanceOf(ApiException.class).hasMessageContaining("entre 1 e 100");
        assertThatThrownBy(() -> service.listar(FilterNotaFiscalDTO.builder().empresaId(3L).dataInicio("02/09/2026").build()))
                .isInstanceOf(ApiException.class).hasMessageContaining("yyyy-MM-dd");
    }
}
