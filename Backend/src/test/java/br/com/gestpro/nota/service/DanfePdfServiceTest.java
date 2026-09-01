package br.com.gestpro.nota.service;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DanfePdfServiceTest {
    @Test
    void geraPdfSomenteAPartirDoXmlAutorizadoVerificado() {
        NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
        FiscalXmlService xmlService = mock(FiscalXmlService.class);
        NotaFiscal nota = new NotaFiscal();
        nota.setTipo(TipoNota.NFE);
        nota.setStatus(NotaFiscalStatus.AUTORIZADA);
        when(repository.findById(7L)).thenReturn(Optional.of(nota));
        when(xmlService.carregarAutorizado(7L)).thenReturn(xml().getBytes(StandardCharsets.UTF_8));

        byte[] pdf = new DanfePdfService(repository, xmlService).gerar(7L);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
        verify(xmlService).carregarAutorizado(7L);
    }

    @Test
    void recusaDocumentoSemAutorizacao() {
        NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
        NotaFiscal nota = new NotaFiscal();
        nota.setTipo(TipoNota.NFE);
        nota.setStatus(NotaFiscalStatus.DIGITACAO);
        when(repository.findById(8L)).thenReturn(Optional.of(nota));
        assertThatThrownBy(() -> new DanfePdfService(repository, mock(FiscalXmlService.class)).gerar(8L))
                .isInstanceOf(RuntimeException.class);
    }

    private String xml() {
        String chave = "35260900000000000191550010000000011000000010";
        return "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><NFe><infNFe Id=\"NFe" + chave + "\">"
                + "<ide><natOp>Venda</natOp><mod>55</mod><serie>1</serie><nNF>1</nNF><dhEmi>2026-09-01T10:00:00-03:00</dhEmi><tpNF>1</tpNF></ide>"
                + "<emit><CNPJ>00000000000191</CNPJ><xNome>Empresa de Homologação</xNome><IE>123</IE></emit>"
                + "<dest><CPF>12345678901</CPF><xNome>Consumidor</xNome><enderDest><xLgr>Rua Teste</xLgr><nro>10</nro><xMun>São Paulo</xMun><UF>SP</UF></enderDest></dest>"
                + "<det nItem=\"1\"><prod><cProd>1</cProd><xProd>Produto de teste</xProd><NCM>12345678</NCM><CFOP>5102</CFOP><uCom>UN</uCom><qCom>1.0000</qCom><vUnCom>10.0000</vUnCom><vProd>10.00</vProd></prod></det>"
                + "<total><ICMSTot><vBC>0.00</vBC><vICMS>0.00</vICMS><vProd>10.00</vProd><vDesc>0.00</vDesc><vNF>10.00</vNF></ICMSTot></total>"
                + "<infAdic><infCpl>Documento emitido em homologação.</infCpl></infAdic></infNFe></NFe>"
                + "<protNFe><infProt><chNFe>" + chave + "</chNFe><dhRecbto>2026-09-01T10:01:00-03:00</dhRecbto><nProt>135260000000001</nProt></infProt></protNFe></nfeProc>";
    }
}
