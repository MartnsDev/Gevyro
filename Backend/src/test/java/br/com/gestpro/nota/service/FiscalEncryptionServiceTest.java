package br.com.gestpro.nota.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class FiscalEncryptionServiceTest {
    private FiscalEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new FiscalEncryptionService(TestFiscalKeys.ephemeralKey());
        service.validateKey();
    }

    @Test
    void cifraComNonceUnicoERestauraConteudo() {
        byte[] plain = "segredo-fiscal".getBytes(StandardCharsets.UTF_8);
        var first = service.encrypt(plain);
        var second = service.encrypt(plain);

        assertThat(first.cipherText()).isNotEqualTo(plain).isNotEqualTo(second.cipherText());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(service.decrypt(first.cipherText(), first.nonce())).isEqualTo(plain);
    }

    @Test
    void rejeitaConteudoCifradoAdulterado() {
        var encrypted = service.encrypt("certificado".getBytes(StandardCharsets.UTF_8));
        encrypted.cipherText()[0] ^= 1;

        assertThatThrownBy(() -> service.decrypt(encrypted.cipherText(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Falha ao proteger segredo fiscal.");
    }

    @Test
    void recusaChaveComTamanhoInseguro() {
        FiscalEncryptionService invalid = new FiscalEncryptionService("Y3VydGE=");
        assertThatThrownBy(invalid::validateKey).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
