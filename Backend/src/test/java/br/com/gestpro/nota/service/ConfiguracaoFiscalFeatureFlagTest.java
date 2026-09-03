package br.com.gestpro.nota.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.RegimeTributario;
import br.com.gestpro.nota.dto.ConfiguracaoFiscalRequest;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import br.com.gestpro.nota.repository.CertificadoDigitalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfiguracaoFiscalFeatureFlagTest {
    private ConfiguracaoFiscalService service;

    @BeforeEach void setup() {
        Usuario dono = mock(Usuario.class); when(dono.getEmail()).thenReturn("dono@empresa.com");
        Empresa empresa = mock(Empresa.class); when(empresa.getDono()).thenReturn(dono);
        EmpresaRepository empresas = mock(EmpresaRepository.class);
        when(empresas.findByIdWithDono(3L)).thenReturn(Optional.of(empresa));
        ConfiguracaoFiscalEmpresaRepository configs = mock(ConfiguracaoFiscalEmpresaRepository.class);
        when(configs.findByEmpresaId(3L)).thenReturn(Optional.empty());
        FiscalAuthorizationService authorization = mock(FiscalAuthorizationService.class);
        when(authorization.exigir(eq(3L), anyString(), any())).thenReturn(empresa);
        service = new ConfiguracaoFiscalService(configs, mock(FiscalEncryptionService.class),
                mock(FiscalAuditService.class), mock(CertificadoDigitalRepository.class), authorization);
    }

    @Test void exigeConfirmacaoParaEntrarEmProducao() {
        assertThatThrownBy(() -> service.salvar(3L, request(true, true, false), "dono@empresa.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("confirmação explícita");
    }

    @Test void naoPermiteDocumentoAtivoComModuloGlobalDesligado() {
        assertThatThrownBy(() -> service.salvar(3L, request(false, true, true), "dono@empresa.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("módulo fiscal");
    }

    private ConfiguracaoFiscalRequest request(boolean modulo, boolean nfe, boolean confirmar) {
        return new ConfiguracaoFiscalRequest("123", RegimeTributario.SIMPLES_NACIONAL,
                ConfiguracaoFiscalEmpresa.Ambiente.PRODUCAO, "1", "1", null, null,
                modulo, nfe, false, false, confirmar);
    }
}
