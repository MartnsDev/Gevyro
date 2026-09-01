package br.com.gestpro.nota.service.validacoes;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SefazResponseParserTest {
    private final SefazComunicacaoService service = new SefazComunicacaoService();

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

    private String resposta(String codigo, String motivo) {
        return "<retEnviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><cStat>104</cStat><xMotivo>Lote processado</xMotivo>"
                + "<protNFe><infProt><cStat>" + codigo + "</cStat><xMotivo>" + motivo
                + "</xMotivo><nProt>12345</nProt></infProt></protNFe></retEnviNFe>";
    }
}
