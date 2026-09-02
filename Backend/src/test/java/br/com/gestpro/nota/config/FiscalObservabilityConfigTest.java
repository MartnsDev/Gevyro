package br.com.gestpro.nota.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalObservabilityConfigTest {
    @Test void carregaExposicaoRestritaAHealthEPrometheus() {
        try (var context = new AnnotationConfigApplicationContext(FiscalObservabilityConfig.class)) {
            assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health,prometheus");
            assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("never");
            assertThat(context.getEnvironment().getProperty("management.prometheus.metrics.export.enabled"))
                    .isEqualTo("true");
        }
    }
}
