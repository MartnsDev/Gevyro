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
}
