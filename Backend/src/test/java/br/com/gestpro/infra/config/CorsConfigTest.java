package br.com.gestpro.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.*;

class CorsConfigTest {

    @Test
    void usaSomenteOrigensExplicitamenteConfiguradas() {
        MockEnvironment environment = new MockEnvironment();
        CorsConfig config = new CorsConfig("http://localhost:3000",
                "http://localhost:3001, https://preview.example.com", environment);

        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/test"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly(
                "http://localhost:3000", "http://localhost:3001", "https://preview.example.com");
        assertThat(cors.getAllowedOrigins()).doesNotContain("https://gevyro.com.br", "https://www.gevyro.com.br");
        assertThat(cors.getAllowedHeaders()).contains("Idempotency-Key", "X-Correlation-ID");
        assertThat(cors.getExposedHeaders()).contains("X-Correlation-ID", "Retry-After");
    }

    @Test
    void rejeitaCuringaComCredenciais() {
        MockEnvironment environment = new MockEnvironment();
        CorsConfig config = new CorsConfig("https://app.example.com", "*", environment);
        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("curinga");
    }

    @Test
    void rejeitaHttpEmProducao() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new CorsConfig("http://app.example.com", "http://app.example.com", environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Origem CORS inválida");
    }

    @Test
    void rejeitaOrigemComCaminhoOuCredenciais() {
        MockEnvironment environment = new MockEnvironment();
        assertThatThrownBy(() -> new CorsConfig("https://usuario@example.com/api", "", environment))
                .isInstanceOf(IllegalStateException.class);
    }
}
