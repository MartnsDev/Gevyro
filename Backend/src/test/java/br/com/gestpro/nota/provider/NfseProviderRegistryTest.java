package br.com.gestpro.nota.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NfseProviderRegistryTest {
    @Test void priorizaPadraoNacionalParaMunicipioValido() {
        NationalNfseProvider nacional = provider();
        NfseProviderRegistry registry = new NfseProviderRegistry(List.of(nacional));

        assertThat(registry.paraMunicipio("3550308")).isSameAs(nacional);
        assertThat(nacional.codigo()).isEqualTo("SEFIN_NACIONAL");
        assertThat(nacional.capacidades()).contains(NfseProvider.Capacidade.EMISSAO_DPS,
                NfseProvider.Capacidade.CONSULTA_CHAVE, NfseProvider.Capacidade.EVENTOS);
    }

    @Test void centralizaEndpointsOficiaisPorAmbiente() {
        NationalNfseProvider provider = provider();
        assertThat(provider.endpoint(true).toString()).isEqualTo(
                "https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional");
        assertThat(provider.endpoint(false).toString()).isEqualTo(
                "https://sefin.nfse.gov.br/SefinNacional");
    }

    @Test void falhaFechadoParaCodigoMunicipalInvalido() {
        NfseProviderRegistry registry = new NfseProviderRegistry(List.of(provider()));
        assertThatThrownBy(() -> registry.paraMunicipio("123"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("IBGE");
    }

    @Test void rejeitaComandoDeEmissaoIncompleto() {
        assertThatThrownBy(() -> provider().emitir(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inválido");
    }

    @Test void converteRespostaHttpAutorizadaSemAlterarXmlOriginal() {
        NfseNationalHttpClient http = mock(NfseNationalHttpClient.class);
        when(http.emitir(any(), eq(true), eq("<DPS/>"), any(), eq("senha"))).thenReturn(
                new NfseNationalHttpClient.Resposta(201, "NFS123", "<NFSe/>", null, null));
        NationalNfseProvider provider = new NationalNfseProvider(http);

        NfseProvider.EmissaoResultado resultado = provider.emitir(new NfseProvider.EmissaoComando(
                "<DPS/>", "3550308", true, new byte[]{1}, "senha"));

        assertThat(resultado.emitida()).isTrue();
        assertThat(resultado.chaveAcesso()).isEqualTo("NFS123");
        assertThat(resultado.nfseXml()).isEqualTo("<NFSe/>");
        assertThat(resultado.codigo()).isEqualTo("201");
    }

    private NationalNfseProvider provider() {
        return new NationalNfseProvider(mock(NfseNationalHttpClient.class));
    }
}
