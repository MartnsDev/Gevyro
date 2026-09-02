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
    private final XmlGeneratorService service = new XmlGeneratorService();

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
}
