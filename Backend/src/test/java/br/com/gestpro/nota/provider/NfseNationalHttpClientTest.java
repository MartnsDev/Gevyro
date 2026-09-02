package br.com.gestpro.nota.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfseNationalHttpClientTest {
    @Test void bloqueiaProducaoPorPadraoAntesDeAbrirConexao() {
        NfseNationalHttpClient client = new NfseNationalHttpClient(new ObjectMapper(), false);
        assertThatThrownBy(() -> client.emitir(URI.create("https://sefin.nfse.gov.br/SefinNacional"),
                false, "<DPS/>", new byte[]{1}, "senha"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("produção não está habilitada");
    }

    @Test void rejeitaEndpointSemHttpsAntesDeLerCertificado() {
        NfseNationalHttpClient client = new NfseNationalHttpClient(new ObjectMapper(), false);
        assertThatThrownBy(() -> client.emitir(URI.create("http://localhost/nfse"),
                true, "<DPS/>", new byte[]{1}, "senha"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("transmissão segura");
    }
}
