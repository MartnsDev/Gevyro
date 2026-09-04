package br.com.gestpro.infra.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.EntityNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveRetornar404GenericoParaRecursoEstaticoInexistente() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");

        ResponseEntity<RetornoErroAPI> response = handler.handleNoResourceFoundException(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().sucesso());
        assertEquals("Recurso não encontrado.", response.getBody().mensagem());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().status());
        assertEquals("/swagger-ui/index.html", response.getBody().path());
        assertNull(response.getBody().erros());
    }

    @Test
    void naoExpoeDetalheInternoEmNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/recurso/1");
        ResponseEntity<RetornoErroAPI> response = handler.handleNotFoundException(
                new EntityNotFoundException("Tabela interna entidade_xyz"), request);
        assertEquals("Recurso não encontrado.", response.getBody().mensagem());
    }

    @Test
    void naoExpoeDetalheInternoEmAcessoNegado() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/fiscal/1");
        ResponseEntity<RetornoErroAPI> response = handler.handleSecurityExceptions(
                new AccessDeniedException("regra-interna-secreta"), request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Acesso negado.", response.getBody().mensagem());
    }
}
