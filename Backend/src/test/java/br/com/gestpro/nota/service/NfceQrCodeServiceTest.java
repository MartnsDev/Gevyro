package br.com.gestpro.nota.service;

import org.junit.jupiter.api.Test;
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
}
