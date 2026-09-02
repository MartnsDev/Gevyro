package br.com.gestpro.nota.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalSensitiveDataRedactorTest {
    private final FiscalSensitiveDataRedactor redactor = new FiscalSensitiveDataRedactor();

    @Test void sanitizaCpfCnpjEmailESegredos() {
        String resultado = redactor.sanitizar(
                "email=pessoa@empresa.com CPF 12345678909 CNPJ 12.345.678/0001-90 apiKey=abc123");

        assertThat(resultado)
                .contains("[EMAIL_REDACTED]", "[DOCUMENTO_REDACTED]", "[SEGREDO_REDACTED]")
                .doesNotContain("pessoa@empresa.com", "12345678909", "12.345.678/0001-90", "abc123");
    }

    @Test void neutralizaQuebrasELimitaTamanho() {
        String resultado = redactor.sanitizar("linha1\nlinha2\t" + "x".repeat(1100));

        assertThat(resultado).doesNotContain("\n", "\t").hasSize(1000);
    }
}
