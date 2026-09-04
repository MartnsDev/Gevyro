package br.com.gestpro.nota.service;

import br.com.gestpro.nota.model.XmlFiscal;
import br.com.gestpro.nota.repository.XmlFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiscalXmlServiceTest {
    private XmlFiscalRepository repository;
    private FiscalXmlService service;

    @BeforeEach void setup() {
        repository = mock(XmlFiscalRepository.class);
        FiscalEncryptionService encryption = new FiscalEncryptionService(TestFiscalKeys.ephemeralKey());
        encryption.validateKey();
        service = new FiscalXmlService(repository, encryption);
    }

    @Test
    void montaNfeProcComNfeAssinadaEProtocoloExato() {
        String nfe = "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe Id=\"NFe1\"/></NFe>";
        String retorno = "<retConsSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><protNFe versao=\"4.00\"><infProt><cStat>100</cStat><nProt>123</nProt></infProt></protNFe></retConsSitNFe>";

        String proc = service.montarNfeProc(nfe, retorno);

        assertThat(proc).contains("<nfeProc").contains("<NFe").contains("<protNFe").contains("<nProt>123</nProt>");
    }

    @Test
    void cifraArmazenaHashEValidaNoDownload() {
        String proc = "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">original</nfeProc>";
        when(repository.findByDocumentoIdAndTipo(7L, XmlFiscal.Tipo.AUTORIZADO)).thenReturn(Optional.empty());
        ArgumentCaptor<XmlFiscal> captor = ArgumentCaptor.forClass(XmlFiscal.class);

        service.armazenarAutorizado(3L, 7L, proc);
        verify(repository).save(captor.capture());
        XmlFiscal salvo = captor.getValue();
        assertThat(new String(salvo.getConteudoCifrado(), StandardCharsets.UTF_8)).doesNotContain("original");
        assertThat(salvo.getSha256()).hasSize(64);

        when(repository.findByDocumentoIdAndTipo(7L, XmlFiscal.Tipo.AUTORIZADO)).thenReturn(Optional.of(salvo));
        assertThat(new String(service.carregarAutorizado(7L), StandardCharsets.UTF_8)).isEqualTo(proc);
    }

    @Test
    void preservaContingenciaCifradaParaRetransmissaoExata() {
        String xml = "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe Id=\"NFe-original\"/></NFe>";
        when(repository.findByDocumentoIdAndTipo(9L, XmlFiscal.Tipo.CONTINGENCIA)).thenReturn(Optional.empty());
        ArgumentCaptor<XmlFiscal> captor = ArgumentCaptor.forClass(XmlFiscal.class);

        service.armazenarContingencia(3L, 9L, xml);

        verify(repository).save(captor.capture());
        XmlFiscal salvo = captor.getValue();
        assertThat(salvo.getTipo()).isEqualTo(XmlFiscal.Tipo.CONTINGENCIA);
        assertThat(salvo.getProvider()).isEqualTo("NFCe_OFFLINE");
        assertThat(new String(salvo.getConteudoCifrado(), StandardCharsets.UTF_8)).doesNotContain("NFe-original");

        when(repository.findByDocumentoIdAndTipo(9L, XmlFiscal.Tipo.CONTINGENCIA)).thenReturn(Optional.of(salvo));
        byte[] recuperado = service.carregarContingencia(9L);
        assertThat(new String(recuperado, StandardCharsets.UTF_8)).isEqualTo(xml);
    }

    @Test
    void impedeSubstituirContingenciaPorDocumentoDiferente() {
        String original = "<NFe>original</NFe>";
        when(repository.findByDocumentoIdAndTipo(9L, XmlFiscal.Tipo.CONTINGENCIA)).thenReturn(Optional.empty());
        ArgumentCaptor<XmlFiscal> captor = ArgumentCaptor.forClass(XmlFiscal.class);
        service.armazenarContingencia(3L, 9L, original);
        verify(repository).save(captor.capture());

        when(repository.findByDocumentoIdAndTipo(9L, XmlFiscal.Tipo.CONTINGENCIA))
                .thenReturn(Optional.of(captor.getValue()));

        assertThatThrownBy(() -> service.armazenarContingencia(3L, 9L, "<NFe>alterado</NFe>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("imutável");
        verify(repository, times(1)).save(any());
    }

    @Test
    void preservaDpsAssinadaCifradaEImutavel() {
        String dps = "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\"><infDPS Id=\"DPS1\"/></DPS>";
        when(repository.findByDocumentoIdAndTipo(11L, XmlFiscal.Tipo.DPS)).thenReturn(Optional.empty());
        ArgumentCaptor<XmlFiscal> captor = ArgumentCaptor.forClass(XmlFiscal.class);

        service.armazenarDps(4L, 11L, dps);
        verify(repository).save(captor.capture());
        XmlFiscal salvo = captor.getValue();
        assertThat(salvo.getTipo()).isEqualTo(XmlFiscal.Tipo.DPS);
        assertThat(salvo.getProvider()).isEqualTo("SEFIN_NACIONAL_LOCAL");
        assertThat(new String(salvo.getConteudoCifrado(), StandardCharsets.UTF_8)).doesNotContain("DPS1");

        when(repository.findByDocumentoIdAndTipo(11L, XmlFiscal.Tipo.DPS)).thenReturn(Optional.of(salvo));
        assertThat(new String(service.carregarDps(11L), StandardCharsets.UTF_8)).isEqualTo(dps);
        assertThatThrownBy(() -> service.armazenarDps(4L, 11L, "<DPS>outra</DPS>"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("imutável");
    }

    @Test
    void rejeitaDoctypeAntesDeProcessarXml() {
        String malicioso = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">&e;</NFe>";
        assertThatThrownBy(() -> service.montarNfeProc(malicioso, "<ret/>")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }
}
