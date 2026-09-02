package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiscalFeatureServiceTest {
    @Test void bloqueiaPorPadraoQuandoEmpresaNaoPossuiConfiguracao() {
        ConfiguracaoFiscalEmpresaRepository repository = mock(ConfiguracaoFiscalEmpresaRepository.class);
        when(repository.findByEmpresaId(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new FiscalFeatureService(repository).validarEmissaoHabilitada(3L, TipoNota.NFE))
                .isInstanceOf(ApiException.class).hasMessageContaining("Configure e habilite");
    }

    @Test void separaHabilitacaoPorTipoDeDocumento() {
        ConfiguracaoFiscalEmpresa config = new ConfiguracaoFiscalEmpresa(3L);
        config.atualizar("123", br.com.gestpro.nota.RegimeTributario.SIMPLES_NACIONAL,
                ConfiguracaoFiscalEmpresa.Ambiente.HOMOLOGACAO, "1", "1", null,
                null, null, false, true, true, false, false);
        ConfiguracaoFiscalEmpresaRepository repository = mock(ConfiguracaoFiscalEmpresaRepository.class);
        when(repository.findByEmpresaId(3L)).thenReturn(Optional.of(config));
        FiscalFeatureService service = new FiscalFeatureService(repository);

        assertThatCode(() -> service.validarEmissaoHabilitada(3L, TipoNota.NFE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validarEmissaoHabilitada(3L, TipoNota.NFCE))
                .isInstanceOf(ApiException.class).hasMessageContaining("não está habilitada");
    }
}
