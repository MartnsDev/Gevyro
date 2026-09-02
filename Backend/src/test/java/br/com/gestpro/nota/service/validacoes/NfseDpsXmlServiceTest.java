package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.nota.dto.DpsNacionalDados;
import br.com.gestpro.nota.service.NfseDpsXmlService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfseDpsXmlServiceTest {
    @Test void geraDpsMinimaCompativelComXsdOficialV101() {
        FiscalXsdValidationService validator = new FiscalXsdValidationService();
        validator.carregarSchema();
        NfseDpsXmlService service = new NfseDpsXmlService(validator);

        String xml = service.gerar(dados("Consultoria & desenvolvimento"));

        assertThat(xml).contains("<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.01\">")
                .contains("Id=\"DPS355030821234567800019500001000000000000123\"")
                .contains("<cTribNac>010101</cTribNac>")
                .contains("Consultoria &amp; desenvolvimento")
                .doesNotContain("<IBSCBS>");
    }

    @Test void rejeitaTributacaoNacionalNaoConfirmada() {
        FiscalXsdValidationService validator = new FiscalXsdValidationService();
        validator.carregarSchema();
        DpsNacionalDados invalido = dados("Serviço");
        invalido = new DpsNacionalDados(invalido.homologacao(), invalido.emissao(), invalido.competencia(),
                invalido.serie(), invalido.numero(), invalido.codigoMunicipioEmissor(), invalido.cnpjPrestador(),
                invalido.inscricaoMunicipal(), invalido.opcaoSimplesNacional(), invalido.regimeEspecialTributacao(),
                invalido.codigoMunicipioPrestacao(), "", invalido.descricaoServico(), invalido.valorServico(),
                invalido.tributacaoIssqn(), invalido.retencaoIssqn(), invalido.aliquotaIssqn());

        DpsNacionalDados finalInvalido = invalido;
        assertThatThrownBy(() -> new NfseDpsXmlService(validator).gerar(finalInvalido))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tributação nacional");
    }

    private DpsNacionalDados dados(String descricao) {
        return new DpsNacionalDados(true,
                OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.ofHours(-3)),
                LocalDate.of(2026, 9, 1), "1", 123, "3550308", "12345678000195", "12345",
                3, 0, "3550308", "010101", descricao, new BigDecimal("100.00"), 1, 1, null);
    }
}
