package br.com.gestpro.infra.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecurityConfigurationTest {
    @Test void producaoApenasValidaSchemaGerenciadoPeloFlyway() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application-prod.properties"));
        assertThat(properties).contains("spring.jpa.hibernate.ddl-auto=validate")
                .doesNotContain("spring.jpa.hibernate.ddl-auto=update");
    }

    @Test void desenvolvimentoNaoVersionaSenhaPadraoDeBanco() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("src/main/resources/application-dev.properties"));
        String properties = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(properties).contains("spring.datasource.password=${DB_PASSWORD}")
                .doesNotContain("spring.datasource.password=${DB_PASSWORD:");
    }
}
