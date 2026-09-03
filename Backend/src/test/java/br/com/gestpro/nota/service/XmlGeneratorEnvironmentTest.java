package br.com.gestpro.nota.service;

import br.com.gestpro.nota.*;
import br.com.gestpro.nota.dto.EmpresaInfo;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.service.validacoes.XmlGeneratorService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlGeneratorEnvironmentTest {
    private final XmlGeneratorService service = new XmlGeneratorService(new NfceQrCodeService());

    @Test
    void escreveAmbienteExplicitamenteNoXml() {
        NotaFiscal nota = NotaFiscal.builder().tipo(TipoNota.NFE).serie("1").numeroNota(1L)
                .dataEmissao(LocalDateTime.of(2026, 9, 1, 10, 0)).naturezaOperacao("Venda")
                .formaPagamento(FormaPagamento.DINHEIRO).build();
        EmpresaInfo empresa = EmpresaInfo.builder().cnpj("12345678000195").razaoSocial("Empresa Teste")
                .logradouro("Rua Teste").numero("1").bairro("Centro").codigoIbge("3550308")
                .municipio("São Paulo").uf("SP").cep("01001000").inscricaoEstadual("110042490114")
                .regimeTributario(RegimeTributario.SIMPLES_NACIONAL).build();
        String chave = "35260912345678000195550010000000011000000019";

        assertThat(service.gerarXmlNfe(nota, empresa, List.of(), chave, true)).contains("<tpAmb>2</tpAmb>");
        assertThat(service.gerarXmlNfe(nota, empresa, List.of(), chave, false)).contains("<tpAmb>1</tpAmb>");
    }

    @Test
    void escreveDoisPagamentosQueFechamTotalDaNfce() {
        NotaFiscal nota = NotaFiscal.builder().tipo(TipoNota.NFCE).serie("1").numeroNota(2L)
                .dataEmissao(LocalDateTime.of(2026, 9, 1, 10, 0)).naturezaOperacao("Venda")
                .formaPagamento(FormaPagamento.PIX).valorPagamento1(new BigDecimal("60.00"))
                .formaPagamento2(FormaPagamento.CARTAO_CREDITO).valorPagamento2(new BigDecimal("40.00"))
                .valorTotal(new BigDecimal("100.00")).build();
        EmpresaInfo empresa = EmpresaInfo.builder().cnpj("12345678000195").razaoSocial("Empresa Teste")
                .logradouro("Rua Teste").numero("1").bairro("Centro").codigoIbge("3550308")
                .municipio("São Paulo").uf("SP").cep("01001000").inscricaoEstadual("110042490114")
                .regimeTributario(RegimeTributario.SIMPLES_NACIONAL).build();
        String xml = service.gerarXmlNfe(nota, empresa, List.of(), "35260912345678000195650010000000021000000013", true);
        assertThat(xml).contains("<tPag>17</tPag><vPag>60.00</vPag>")
                .contains("<tPag>03</tPag><vPag>40.00</vPag>");
    }

    @Test
    void bloqueiaPagamentosQueNaoFechamTotal() {
        NotaFiscal nota = NotaFiscal.builder().tipo(TipoNota.NFCE).serie("1").numeroNota(2L)
                .dataEmissao(LocalDateTime.now()).naturezaOperacao("Venda").formaPagamento(FormaPagamento.PIX)
                .valorPagamento1(new BigDecimal("50.00")).formaPagamento2(FormaPagamento.DINHEIRO)
                .valorPagamento2(new BigDecimal("40.00")).valorTotal(new BigDecimal("100.00")).build();
        EmpresaInfo empresa = EmpresaInfo.builder().cnpj("12345678000195").razaoSocial("Empresa Teste")
                .logradouro("Rua").numero("1").bairro("Centro").codigoIbge("3550308").municipio("São Paulo")
                .uf("SP").cep("01001000").inscricaoEstadual("110042490114").regimeTributario(RegimeTributario.SIMPLES_NACIONAL).build();
        assertThatThrownBy(() -> service.gerarXmlNfe(nota, empresa, List.of(),
                "35260912345678000195650010000000021000000013", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incluiQrCodeVersao3OnlineAntesDaAssinatura() {
        NotaFiscal nota = NotaFiscal.builder().tipo(TipoNota.NFCE).serie("1").numeroNota(2L)
                .dataEmissao(LocalDateTime.of(2026, 9, 1, 10, 0)).naturezaOperacao("Venda")
                .formaPagamento(FormaPagamento.PIX).valorTotal(new BigDecimal("10.00")).build();
        EmpresaInfo empresa = EmpresaInfo.builder().cnpj("12345678000195").razaoSocial("Empresa Teste")
                .logradouro("Rua").numero("1").bairro("Centro").codigoIbge("3550308").municipio("São Paulo")
                .uf("SP").cep("01001000").inscricaoEstadual("110042490114").regimeTributario(RegimeTributario.SIMPLES_NACIONAL).build();
        String chave = "35260912345678000195650010000000021000000013";
        String xml = service.gerarXmlNfe(nota, empresa, List.of(), chave, true);
        assertThat(xml).contains("<infNFeSupl><qrCode><![CDATA[https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode?p="
                        + chave + "|3|2]]></qrCode>")
                .contains("<urlChave>https://www.homologacao.nfce.fazenda.sp.gov.br/consulta</urlChave>")
                .doesNotContain("cIdToken");
        assertThat(xml.indexOf("</infNFe>")).isLessThan(xml.indexOf("<infNFeSupl>"));
    }

    @Test
    void bloqueiaNfceOfflineSemQrAssinado() {
        NotaFiscal nota = NotaFiscal.builder().tipo(TipoNota.NFCE).serie("1").numeroNota(2L)
                .dataEmissao(LocalDateTime.now()).naturezaOperacao("Venda").formaPagamento(FormaPagamento.PIX)
                .valorTotal(BigDecimal.ONE).emContingencia(true)
                .dataInicioContingencia(LocalDateTime.of(2026, 9, 3, 12, 0))
                .justificativaContingencia("Indisponibilidade comprovada da autorizadora").build();
        EmpresaInfo empresa = EmpresaInfo.builder().cnpj("12345678000195").razaoSocial("Empresa")
                .logradouro("Rua").numero("1").bairro("Centro").codigoIbge("3550308").municipio("São Paulo")
                .uf("SP").cep("01001000").inscricaoEstadual("110042490114").regimeTributario(RegimeTributario.SIMPLES_NACIONAL).build();
        assertThatThrownBy(() -> service.gerarXmlNfe(nota, empresa, List.of(),
                "35260912345678000195650010000000029999999990", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("QR Code v3 assinado");
    }

    @Test void incluiDadosObrigatoriosDaContingenciaESeuQrAssinado() {
        NotaFiscal nota = NotaFiscal.builder().tipo(TipoNota.NFCE).serie("1").numeroNota(2L)
                .dataEmissao(LocalDateTime.of(2026, 9, 3, 11, 0)).naturezaOperacao("Venda")
                .formaPagamento(FormaPagamento.PIX).valorTotal(BigDecimal.ONE).emContingencia(true)
                .dataInicioContingencia(LocalDateTime.of(2026, 9, 3, 12, 0))
                .justificativaContingencia("Indisponibilidade comprovada da autorizadora").build();
        EmpresaInfo empresa = EmpresaInfo.builder().cnpj("12345678000195").razaoSocial("Empresa")
                .logradouro("Rua").numero("1").bairro("Centro").codigoIbge("3550308").municipio("São Paulo")
                .uf("SP").cep("01001000").inscricaoEstadual("110042490114").regimeTributario(RegimeTributario.SIMPLES_NACIONAL).build();
        var qr = new NfceQrCodeService.DadosQrCode("https://exemplo.invalid/?p=assinado",
                "https://consulta.invalid", "3.00", false);
        String xml = service.gerarXmlNfe(nota, empresa, List.of(),
                "35260912345678000195650010000000029999999990", true, qr);
        assertThat(xml).contains("<tpEmis>9</tpEmis>")
                .contains("<dhCont>2026-09-03T12:00:00-03:00</dhCont>")
                .contains("<xJust>Indisponibilidade comprovada da autorizadora</xJust>")
                .contains("<![CDATA[https://exemplo.invalid/?p=assinado]]>");
    }
}
