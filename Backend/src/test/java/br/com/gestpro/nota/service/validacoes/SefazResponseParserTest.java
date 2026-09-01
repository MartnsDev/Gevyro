package br.com.gestpro.nota.service.validacoes;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SefazResponseParserTest {
    private final SefazComunicacaoService service = new SefazComunicacaoService(null, null);

    @Test void loteProcessadoUsaAutorizacaoInterna() {
        var retorno = service.parseRetornoSefaz(resposta("100", "Autorizado o uso da NF-e"));
        assertThat(retorno.isSucesso()).isTrue();
        assertThat(retorno.getCodigo()).isEqualTo("100");
        assertThat(retorno.getProtocolo()).isEqualTo("12345");
    }

    @Test void loteProcessadoNaoOcultaRejeicaoInterna() {
        var retorno = service.parseRetornoSefaz(resposta("539", "Duplicidade de NF-e"));
        assertThat(retorno.isSucesso()).isFalse();
        assertThat(retorno.getCodigo()).isEqualTo("539");
        assertThat(retorno.getMensagem()).isEqualTo("Duplicidade de NF-e");
    }

    @Test void eventoRegistradoEReconhecidoComoAceito() {
        String xml = "<retEnvEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\"><cStat>128</cStat>"
                + "<retEvento><infEvento><cStat>135</cStat><xMotivo>Evento registrado</xMotivo>"
                + "<nProt>987</nProt></infEvento></retEvento></retEnvEvento>";
        var retorno = service.parseRetornoSefaz(xml);
        assertThat(retorno.isSucesso()).isTrue();
        assertThat(retorno.getCodigo()).isEqualTo("135");
    }

    @Test void cancelamentoEscapaJustificativaSemPermitirInjecaoXml() {
        String xml = service.buildXmlCancelamento(
                "35260900000000000191550010000000011000000010",
                "135260000000001", "Cancelamento por erro <item> & revisão", true);

        assertThat(xml).contains("Cancelamento por erro &lt;item&gt; &amp; revisão");
        assertThat(xml).doesNotContain("<item>");
    }

    @Test void cancelamentoRejeitaChaveEProtocoloInvalidos() {
        assertThatThrownBy(() -> service.buildXmlCancelamento(
                "123", "abc", "Justificativa suficientemente longa", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void montaInutilizacaoComIdOficialEConteudoEscapado() {
        String xml = service.buildXmlInutilizacao("00.000.000/0001-91", "SP", "55", 2026, 1,
                10, 12, "Numeração não utilizada & inutilizada", true);
        assertThat(xml).contains("Id=\"ID35260000000000019155001000000010000000012\"");
        assertThat(xml).contains("<tpAmb>2</tpAmb>").contains("&amp;");
    }

    private String resposta(String codigo, String motivo) {
        return "<retEnviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><cStat>104</cStat><xMotivo>Lote processado</xMotivo>"
                + "<protNFe><infProt><cStat>" + codigo + "</cStat><xMotivo>" + motivo
                + "</xMotivo><nProt>12345</nProt></infProt></protNFe></retEnviNFe>";
    }
}
