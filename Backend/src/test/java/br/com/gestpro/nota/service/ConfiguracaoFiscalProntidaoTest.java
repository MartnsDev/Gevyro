package br.com.gestpro.nota.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.RegimeTributario;
import br.com.gestpro.nota.model.CertificadoDigital;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.repository.CertificadoDigitalRepository;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfiguracaoFiscalProntidaoTest {
    private EmpresaRepository empresas;
    private ConfiguracaoFiscalEmpresaRepository configuracoes;
    private CertificadoDigitalRepository certificados;
    private FiscalAuthorizationService authorization;
    private ConfiguracaoFiscalService service;

    @BeforeEach void setup() {
        empresas = mock(EmpresaRepository.class);
        configuracoes = mock(ConfiguracaoFiscalEmpresaRepository.class);
        certificados = mock(CertificadoDigitalRepository.class);
        authorization = mock(FiscalAuthorizationService.class);
        service = new ConfiguracaoFiscalService(configuracoes, mock(FiscalEncryptionService.class),
                mock(FiscalAuditService.class), certificados, authorization);
    }

    @Test void informaDocumentosProntosSemLiberarNfse() {
        when(authorization.exigir(eq(7L), eq("dono@empresa.com"), any())).thenReturn(empresaCompleta("dono@empresa.com"));
        when(configuracoes.findByEmpresaId(7L)).thenReturn(Optional.of(configuracaoCompleta(true)));
        when(certificados.findByEmpresaId(7L)).thenReturn(Optional.of(certificadoVigente()));

        var resposta = service.prontidao(7L, "dono@empresa.com");

        assertThat(resposta.percentual()).isEqualTo(100);
        assertThat(resposta.nfePronta()).isTrue();
        assertThat(resposta.nfcePronta()).isTrue();
        assertThat(resposta.nfsePronta()).isFalse();
        assertThat(resposta.alertas()).anyMatch(a -> a.contains("NFS-e"));
    }

    @Test void cscAusenteBloqueiaSomenteNfce() {
        when(authorization.exigir(eq(7L), eq("dono@empresa.com"), any())).thenReturn(empresaCompleta("dono@empresa.com"));
        when(configuracoes.findByEmpresaId(7L)).thenReturn(Optional.of(configuracaoCompleta(false)));
        when(certificados.findByEmpresaId(7L)).thenReturn(Optional.of(certificadoVigente()));

        var resposta = service.prontidao(7L, "dono@empresa.com");

        assertThat(resposta.nfePronta()).isTrue();
        assertThat(resposta.nfcePronta()).isFalse();
        assertThat(resposta.requisitos()).filteredOn(r -> r.codigo().equals("CSC"))
                .allMatch(r -> !r.concluido());
    }

    @Test void rejeitaConsultaDeOutraEmpresa() {
        when(authorization.exigir(eq(7L), eq("intruso@empresa.com"), any())).thenThrow(new ApiException(
                "Sem permissão para esta operação fiscal.", org.springframework.http.HttpStatus.FORBIDDEN, "/api/fiscal"));
        assertThatThrownBy(() -> service.prontidao(7L, "intruso@empresa.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Sem permissão");
        verifyNoInteractions(configuracoes, certificados);
    }

    private Empresa empresaCompleta(String email) {
        Usuario dono = new Usuario(); dono.setEmail(email);
        Empresa empresa = new Empresa(); empresa.setDono(dono); empresa.setCnpj("12.345.678/0001-90");
        empresa.setRazaoSocial("Empresa Teste Ltda"); empresa.setNomeFantasia("Empresa Teste");
        empresa.setCep("01001000"); empresa.setLogradouro("Praça da Sé"); empresa.setNumero("1");
        empresa.setBairro("Sé"); empresa.setCidade("São Paulo"); empresa.setUf("SP");
        return empresa;
    }

    private ConfiguracaoFiscalEmpresa configuracaoCompleta(boolean comCsc) {
        ConfiguracaoFiscalEmpresa config = new ConfiguracaoFiscalEmpresa(7L);
        config.atualizar("110042490114", RegimeTributario.SIMPLES_NACIONAL,
                ConfiguracaoFiscalEmpresa.Ambiente.HOMOLOGACAO, "1", "1", comCsc ? "1" : null,
                comCsc ? new byte[]{1} : null, comCsc ? new byte[12] : null, true,
                true, true, true, false);
        config.atualizarDadosEmitente(null, null, "3550308", null, null);
        return config;
    }

    private CertificadoDigital certificadoVigente() {
        CertificadoDigital certificado = new CertificadoDigital(7L);
        certificado.substituir(new byte[]{1}, new byte[12], new byte[]{2}, new byte[12], "Titular", "AC Teste", "1",
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(90L * 24 * 60 * 60));
        return certificado;
    }
}
