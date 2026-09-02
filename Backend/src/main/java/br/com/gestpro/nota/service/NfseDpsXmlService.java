package br.com.gestpro.nota.service;

import br.com.gestpro.nota.dto.DpsNacionalDados;
import br.com.gestpro.nota.service.validacoes.FiscalXsdValidationService;
import br.com.gestpro.nota.service.validacoes.AssinaturaDigitalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/** Gera a DPS nacional v1.01 sem assinatura e a valida imediatamente pelo XSD oficial. */
@Service
@RequiredArgsConstructor
public class NfseDpsXmlService {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ISO_LOCAL_DATE;
    private final FiscalXsdValidationService xsd;
    private final AssinaturaDigitalService assinatura;

    public String gerarAssinada(DpsNacionalDados dados, byte[] pfx, String senhaCertificado) {
        if (pfx == null || pfx.length == 0 || senhaCertificado == null)
            throw new IllegalArgumentException("Certificado A1 e senha são obrigatórios para assinar a DPS.");
        String xml = gerar(dados);
        try {
            String assinado = assinatura.assinarDps(xml, pfx, senhaCertificado);
            xsd.validarDpsNacional(assinado);
            return assinado;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar a DPS com o certificado da empresa.", e);
        }
    }

    public String gerar(DpsNacionalDados d) {
        validar(d);
        String cnpj = digitos(d.cnpjPrestador());
        String serie = String.valueOf(Long.parseLong(d.serie()));
        String id = "DPS" + d.codigoMunicipioEmissor() + "2" + cnpj
                + completar(serie, 5) + completar(Long.toString(d.numero()), 15);
        StringBuilder xml = new StringBuilder(1800);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.01\">")
                .append("<infDPS Id=\"").append(id).append("\">")
                .append("<tpAmb>").append(d.homologacao() ? "2" : "1").append("</tpAmb>")
                .append("<dhEmi>").append(d.emissao().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("</dhEmi>")
                .append("<verAplic>Gevyro-1.0</verAplic><serie>").append(serie).append("</serie><nDPS>")
                .append(d.numero()).append("</nDPS><dCompet>").append(d.competencia().format(DATA))
                .append("</dCompet><tpEmit>1</tpEmit><cLocEmi>").append(d.codigoMunicipioEmissor()).append("</cLocEmi>")
                .append("<prest><CNPJ>").append(cnpj).append("</CNPJ>");
        if (d.inscricaoMunicipal() != null && !d.inscricaoMunicipal().isBlank())
            xml.append("<IM>").append(esc(d.inscricaoMunicipal().trim())).append("</IM>");
        xml.append("<regTrib><opSimpNac>").append(d.opcaoSimplesNacional()).append("</opSimpNac>")
                .append("<regEspTrib>").append(d.regimeEspecialTributacao()).append("</regEspTrib></regTrib></prest>")
                .append("<serv><locPrest><cLocPrestacao>").append(d.codigoMunicipioPrestacao())
                .append("</cLocPrestacao></locPrest><cServ><cTribNac>").append(d.codigoTributacaoNacional())
                .append("</cTribNac><xDescServ>").append(esc(d.descricaoServico().trim()))
                .append("</xDescServ></cServ></serv><valores><vServPrest><vServ>")
                .append(d.valorServico().setScale(2, RoundingMode.HALF_UP)).append("</vServ></vServPrest>")
                .append("<trib><tribMun><tribISSQN>").append(d.tributacaoIssqn()).append("</tribISSQN>")
                .append("<tpRetISSQN>").append(d.retencaoIssqn()).append("</tpRetISSQN>");
        if (d.aliquotaIssqn() != null)
            xml.append("<pAliq>").append(d.aliquotaIssqn().setScale(2, RoundingMode.HALF_UP)).append("</pAliq>");
        xml.append("</tribMun><totTrib><indTotTrib>0</indTotTrib></totTrib></trib></valores></infDPS></DPS>");
        String resultado = xml.toString();
        xsd.validarDpsNacional(resultado);
        return resultado;
    }

    private void validar(DpsNacionalDados d) {
        if (d == null || d.emissao() == null || d.competencia() == null)
            throw new IllegalArgumentException("Emissão e competência da DPS são obrigatórias.");
        if (!digitos(d.cnpjPrestador()).matches("[0-9]{14}")) throw new IllegalArgumentException("CNPJ do prestador inválido.");
        if (d.serie() == null || !d.serie().matches("[0-9]{1,5}") || d.numero() < 1 || d.numero() > 999_999_999_999_999L)
            throw new IllegalArgumentException("Série ou número da DPS inválido.");
        if (!municipio(d.codigoMunicipioEmissor()) || !municipio(d.codigoMunicipioPrestacao()))
            throw new IllegalArgumentException("Código IBGE municipal inválido.");
        if (d.codigoTributacaoNacional() == null || !d.codigoTributacaoNacional().matches("[0-9]{6}"))
            throw new IllegalArgumentException("Código de tributação nacional inválido.");
        if (d.descricaoServico() == null || d.descricaoServico().isBlank() || d.descricaoServico().length() > 2000)
            throw new IllegalArgumentException("Descrição do serviço inválida.");
        if (d.valorServico() == null || d.valorServico().signum() <= 0)
            throw new IllegalArgumentException("Valor do serviço deve ser positivo.");
        if (d.opcaoSimplesNacional() < 1 || d.opcaoSimplesNacional() > 3 || d.regimeEspecialTributacao() < 0
                || (d.regimeEspecialTributacao() > 6 && d.regimeEspecialTributacao() != 9))
            throw new IllegalArgumentException("Regime tributário da DPS inválido.");
        if (d.tributacaoIssqn() < 1 || d.tributacaoIssqn() > 4 || d.retencaoIssqn() < 1 || d.retencaoIssqn() > 3)
            throw new IllegalArgumentException("Tratamento do ISSQN inválido.");
    }

    private boolean municipio(String valor) { return valor != null && valor.matches("[0-9]{7}"); }
    private String digitos(String valor) { return valor == null ? "" : valor.replaceAll("\\D", ""); }
    private String completar(String valor, int tamanho) { return "0".repeat(tamanho - valor.length()) + valor; }
    private String esc(String valor) { return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;"); }
}
