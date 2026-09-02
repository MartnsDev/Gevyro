package br.com.gestpro.nota.service;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.nota.service.validacoes.FiscalXsdValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NfeXmlImportServiceTest {
    private NfeXmlImportService service;

    @BeforeEach void setup() {
        Empresa empresa = mock(Empresa.class);
        when(empresa.getCnpj()).thenReturn("12.345.678/0001-95");
        EmpresaRepository empresas = mock(EmpresaRepository.class);
        when(empresas.findById(3L)).thenReturn(Optional.of(empresa));
        service = new NfeXmlImportService(empresas, mock(NotaFiscalRepository.class),
                mock(FiscalXsdValidationService.class), mock(FiscalXmlService.class));
    }

    @Test void bloqueiaXxeAntesDeAcessarConteudoExterno() {
        byte[] xml = ("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\">&e;</nfeProc>").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.importar(3L, xml)).isInstanceOf(ApiException.class)
                .hasMessageContaining("insegura");
    }

    @Test void bloqueiaXmlMaiorQueDoisMibAntesDoParser() {
        assertThatThrownBy(() -> service.importar(3L, new byte[NfeXmlImportService.MAX_XML_BYTES + 1]))
                .isInstanceOf(ApiException.class).hasMessageContaining("2 MiB");
    }

    @Test void rejeitaSequenciaUtf8Invalida() {
        assertThatThrownBy(() -> service.importar(3L, new byte[] {(byte) 0xC3, (byte) 0x28}))
                .isInstanceOf(ApiException.class).hasMessageContaining("UTF-8");
    }

    @Test void bloqueiaDocumentoDeOutroEmitenteAntesDePersistir() {
        String chave = "35260999999999000199550010000000011000000010";
        String xml = "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\"><NFe><infNFe Id=\"NFe" + chave + "\">"
                + "<emit><CNPJ>99999999000199</CNPJ></emit></infNFe></NFe><protNFe><infProt>"
                + "<chNFe>" + chave + "</chNFe><cStat>100</cStat></infProt></protNFe></nfeProc>";
        assertThatThrownBy(() -> service.importar(3L, xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class).hasMessageContaining("não pertence");
    }
}
