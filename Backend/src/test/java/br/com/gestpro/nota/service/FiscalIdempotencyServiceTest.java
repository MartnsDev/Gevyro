package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.model.FiscalIdempotency;
import br.com.gestpro.nota.repository.FiscalIdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiscalIdempotencyServiceTest {
    private FiscalIdempotencyRepository repository;
    private FiscalIdempotencyWriter writer;
    private FiscalIdempotencyService service;

    @BeforeEach void setUp() {
        repository = mock(FiscalIdempotencyRepository.class);
        writer = mock(FiscalIdempotencyWriter.class);
        service = new FiscalIdempotencyService(repository, writer);
    }

    @Test void exigeChaveNaEmissao() {
        assertThatThrownBy(() -> service.iniciarEmissao(1L, 2L, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("obrigatória");
    }

    @Test void rejeitaReusoDaChaveEmOutroDocumento() {
        FiscalIdempotency existente = new FiscalIdempotency(1L, 99L, "EMISSAO", "chave-123");
        when(repository.findByEmpresaIdAndOperacaoAndIdempotencyKey(1L, "EMISSAO", "chave-123"))
                .thenReturn(Optional.of(existente));
        assertThatThrownBy(() -> service.iniciarEmissao(1L, 2L, "chave-123"))
                .isInstanceOf(ApiException.class).hasMessageContaining("outra nota");
    }

    @Test void reconheceOperacaoConcluida() {
        FiscalIdempotency existente = new FiscalIdempotency(1L, 2L, "EMISSAO", "chave-123");
        existente.concluir();
        when(repository.findByEmpresaIdAndOperacaoAndIdempotencyKey(1L, "EMISSAO", "chave-123"))
                .thenReturn(Optional.of(existente));
        assertThat(service.iniciarEmissao(1L, 2L, "chave-123").concluida()).isTrue();
        verifyNoInteractions(writer);
    }
}
