package br.com.gestpro.nota.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class NfceQrCodeServiceTest {
    private final NfceQrCodeService service = new NfceQrCodeService();
    private static final String CHAVE = "35260912345678000195650010000000021000000013";

    @Test void geraVersao3OnlineHomologacaoSemCsc() {
        var dados = service.gerarOnline(CHAVE, "SP", true);
        assertThat(dados.qrCodeUrl()).isEqualTo(
                "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode?p=" + CHAVE + "|3|2");
        assertThat(dados.consultaUrl()).isEqualTo("https://www.homologacao.nfce.fazenda.sp.gov.br/consulta");
        assertThat(dados.versao()).isEqualTo("3.00");
        assertThat(dados.usaCsc()).isFalse();
    }

    @Test void geraVersao3OnlineProducaoSemCsc() {
        assertThat(service.gerarOnline(CHAVE, "SP", false).qrCodeUrl())
                .isEqualTo("https://www.nfce.fazenda.sp.gov.br/qrcode?p=" + CHAVE + "|3|1");
    }

    @Test void falhaFechadoParaUfNaoValidada() {
        assertThatThrownBy(() -> service.gerarOnline(CHAVE.replaceFirst("35", "31"), "MG", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("não foi validada");
    }

    @Test void rejeitaChaveMalformada() {
        assertThatThrownBy(() -> service.gerarOnline("123", "SP", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void geraEAssinaVersao3OfflineComSeparadoresDeDestinatarioAusente() throws Exception {
        var par = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var data = LocalDateTime.of(2026, 9, 3, 12, 0);
        var dados = service.gerarOffline(CHAVE, "SP", true, data, new BigDecimal("10.50"), null, par.getPrivate());
        String prefixo = "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode?p=";
        String payload = java.net.URLDecoder.decode(dados.qrCodeUrl().substring(prefixo.length()), java.nio.charset.StandardCharsets.UTF_8);
        String[] partes = payload.split("\\|", -1);
        assertThat(partes).hasSize(8);
        assertThat(partes[0]).isEqualTo(CHAVE);
        assertThat(partes[1]).isEqualTo("3");
        assertThat(partes[2]).isEqualTo("2");
        assertThat(partes[3]).isEqualTo("03");
        assertThat(partes[4]).isEqualTo("10.50");
        assertThat(partes[5]).isEmpty();
        assertThat(partes[6]).isEmpty();
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(par.getPublic());
        verifier.update(String.join("|", java.util.Arrays.copyOf(partes, 7)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getDecoder().decode(partes[7]))).isTrue();
    }

    @Test void rejeitaDocumentoDestinatarioInvalidoNoOffline() throws Exception {
        var chave = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate();
        assertThatThrownBy(() -> service.gerarOffline(CHAVE, "SP", true, LocalDateTime.now(),
                BigDecimal.ONE, "123", chave)).isInstanceOf(IllegalArgumentException.class);
    }
}
