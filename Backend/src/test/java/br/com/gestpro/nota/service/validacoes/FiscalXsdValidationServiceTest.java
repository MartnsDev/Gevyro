package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.infra.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiscalXsdValidationServiceTest {
    private FiscalXsdValidationService service;

    @BeforeEach
    void setUp() {
        service = new FiscalXsdValidationService();
        assertDoesNotThrow(service::carregarSchema);
    }

    @Test
    void rejeitaXmlQueNaoSegueLeiauteOficial() {
        assertThrows(ApiException.class,
                () -> service.validarNfeAssinada("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"/>"));
    }

    @Test
    void bloqueiaDoctypeEEntidadeExterna() {
        String xxe = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE NFe [<!ENTITY arquivo SYSTEM \"file:///etc/passwd\">]>"
                + "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">&arquivo;</NFe>";
        assertThrows(ApiException.class, () -> service.validarNfeAssinada(xxe));
    }

    @Test
    void rejeitaXmlAcimaDoLimiteAntesDoParser() {
        String excessivo = "x".repeat(FiscalXsdValidationService.MAX_XML_BYTES + 1);
        assertThrows(ApiException.class, () -> service.validarNfeAssinada(excessivo));
    }

    @Test
    void carregaSchemaOficialDeInutilizacaoEExigeAssinatura() {
        String xmlSemAssinatura = "<inutNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<infInut Id=\"ID35260000000000019155001000000010000000012\"><tpAmb>2</tpAmb>"
                + "<xServ>INUTILIZAR</xServ><cUF>35</cUF><ano>26</ano><CNPJ>00000000000191</CNPJ>"
                + "<mod>55</mod><serie>1</serie><nNFIni>10</nNFIni><nNFFin>12</nNFFin>"
                + "<xJust>Numeração não utilizada pelo contribuinte</xJust></infInut></inutNFe>";
        assertThrows(ApiException.class, () -> service.validarInutilizacaoAssinada(xmlSemAssinatura));
    }
}
