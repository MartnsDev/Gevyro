package br.com.gestpro.auth.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void naoDeveSerializarCredenciaisOuTokens() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setEmail("usuario@example.com");
        usuario.setSenha("senha-hash");
        usuario.setTokenConfirmacao("token-confirmacao");
        usuario.setCodigoRecuperacao("123456");

        String json = objectMapper.writeValueAsString(usuario);

        assertThat(json)
                .doesNotContain("senha")
                .doesNotContain("senha-hash")
                .doesNotContain("tokenConfirmacao")
                .doesNotContain("token-confirmacao")
                .doesNotContain("codigoRecuperacao")
                .doesNotContain("123456");
    }
}
