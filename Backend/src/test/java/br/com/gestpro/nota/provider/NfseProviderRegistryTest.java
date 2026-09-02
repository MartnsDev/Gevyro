package br.com.gestpro.nota.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfseProviderRegistryTest {
    @Test void priorizaPadraoNacionalParaMunicipioValido() {
        NationalNfseProvider nacional = new NationalNfseProvider();
        NfseProviderRegistry registry = new NfseProviderRegistry(List.of(nacional));

        assertThat(registry.paraMunicipio("3550308")).isSameAs(nacional);
        assertThat(nacional.codigo()).isEqualTo("SEFIN_NACIONAL");
        assertThat(nacional.capacidades()).contains(NfseProvider.Capacidade.EMISSAO_DPS,
                NfseProvider.Capacidade.CONSULTA_CHAVE, NfseProvider.Capacidade.EVENTOS);
    }

    @Test void centralizaEndpointsOficiaisPorAmbiente() {
        NationalNfseProvider provider = new NationalNfseProvider();
        assertThat(provider.endpoint(true).toString()).isEqualTo(
                "https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional");
        assertThat(provider.endpoint(false).toString()).isEqualTo(
                "https://sefin.nfse.gov.br/SefinNacional");
    }

    @Test void falhaFechadoParaCodigoMunicipalInvalido() {
        NfseProviderRegistry registry = new NfseProviderRegistry(List.of(new NationalNfseProvider()));
        assertThatThrownBy(() -> registry.paraMunicipio("123"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("IBGE");
    }

    @Test void naoSimulaEmissaoAntesDoDpsOficial() {
        assertThatThrownBy(() -> new NationalNfseProvider().emitir(null))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("DPS v1.01");
    }
}
