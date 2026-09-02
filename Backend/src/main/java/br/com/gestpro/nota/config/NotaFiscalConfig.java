package br.com.gestpro.nota.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class NotaFiscalConfig {
    public enum SefazService { AUTORIZACAO, CONSULTA_PROTOCOLO, STATUS_SERVICO, EVENTO, INUTILIZACAO }

    /**
     * URLs dos webservices SEFAZ por UF.
     * Homologação e Produção para NF-e (modelo 55) e NFC-e (modelo 65).
     * Fonte: Portal Nacional da NF-e (nfe.fazenda.gov.br)
     */
    public static final Map<String, SefazUrls> WEBSERVICES_NFE = new HashMap<>();
    public static final Map<String, SefazUrls> WEBSERVICES_NFCE = new HashMap<>();

    static {
        // NF-e URLs por UF
        WEBSERVICES_NFE.put("SP", new SefazUrls(
                "https://homologacao.nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx",
                "https://nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx"
        ));
        WEBSERVICES_NFE.put("MG", new SefazUrls(
                "https://hnfe.fazenda.mg.gov.br/nfe2/services/NFeAutorizacao4",
                "https://nfe.fazenda.mg.gov.br/nfe2/services/NFeAutorizacao4"
        ));
        WEBSERVICES_NFE.put("RS", new SefazUrls(
                "https://nfe-homologacao.sefazrs.rs.gov.br/ws/nfeFalha/nfeFalha.asmx",
                "https://nfe.sefazrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx"
        ));
        WEBSERVICES_NFE.put("PR", new SefazUrls(
                "https://homologacao.nfe2.fazenda.pr.gov.br/nfe/NFeAutorizacao4",
                "https://nfe2.fazenda.pr.gov.br/nfe/NFeAutorizacao4"
        ));
        WEBSERVICES_NFE.put("SC", new SefazUrls(
                "https://hom.nfe.sef.sc.gov.br/ws/NFeAutorizacao4/NFeAutorizacao4.asmx",
                "https://nfe.sef.sc.gov.br/nfe/services/NFeAutorizacao4"
        ));
        // SVAN (Sefaz Virtual Ambiente Nacional) - estados que usam SVRS ou SVAN
        WEBSERVICES_NFE.put("DEFAULT", new SefazUrls(
                "https://hom.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx",
                "https://nfe.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx"
        ));

        // NFC-e URLs por UF
        WEBSERVICES_NFCE.put("SP", new SefazUrls(
                "https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeAutorizacao4.asmx",
                "https://nfce.fazenda.sp.gov.br/ws/NFeAutorizacao4.asmx"
        ));
        WEBSERVICES_NFCE.put("MG", new SefazUrls(
                "https://hnfe.fazenda.mg.gov.br/nfce/services/NFeAutorizacao4",
                "https://nfe.fazenda.mg.gov.br/nfce/services/NFeAutorizacao4"
        ));
        WEBSERVICES_NFCE.put("DEFAULT", new SefazUrls(
                "https://hom.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx",
                "https://nfe.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx"
        ));
    }

    /**
     * Versão do leiaute da NF-e em uso.
     */
    public static final String VERSAO_NFCE = "4.00";
    public static final String VERSAO_NFE = "4.00";

    /**
     * Ambiente: 1 = Produção, 2 = Homologação
     */
    public static final int AMBIENTE_PRODUCAO = 1;
    public static final int AMBIENTE_HOMOLOGACAO = 2;

    /**
     * Retorna a URL correta do webservice para NF-e ou NFC-e baseado na UF e ambiente.
     */
    public static String getWebserviceUrl(String uf, String modelo, boolean homologacao) {
        Map<String, SefazUrls> mapa = "65".equals(modelo) ? WEBSERVICES_NFCE : WEBSERVICES_NFE;
        SefazUrls urls = mapa.getOrDefault(uf, mapa.get("DEFAULT"));
        return homologacao ? urls.getHomologacao() : urls.getProducao();
    }

    /** Endpoints por serviço que foram conferidos individualmente no Portal Nacional. */
    public static String getWebserviceUrl(String uf, String modelo, boolean homologacao, SefazService service) {
        if (service == SefazService.AUTORIZACAO) return getWebserviceUrl(uf, modelo, homologacao);
        if (!"SP".equalsIgnoreCase(uf)) {
            throw new IllegalStateException("Endpoint " + service + " ainda não foi validado para a UF " + uf + ".");
        }
        String prefix = homologacao ? "https://homologacao.nfe.fazenda.sp.gov.br/ws/"
                : "https://nfe.fazenda.sp.gov.br/ws/";
        return prefix + switch (service) {
            case CONSULTA_PROTOCOLO -> "nfeconsultaprotocolo4.asmx";
            case STATUS_SERVICO -> "nfestatusservico4.asmx";
            case EVENTO -> "nferecepcaoevento4.asmx";
            case INUTILIZACAO -> "nfeinutilizacao4.asmx";
            default -> throw new IllegalArgumentException("Serviço inválido.");
        };
    }

    /** URLs oficiais de consulta NFC-e, verificadas individualmente por UF. */
    public static String getNfceQrCodeUrl(String uf, boolean homologacao) {
        if (!"SP".equalsIgnoreCase(uf))
            throw new IllegalStateException("URL de QR Code NFC-e ainda não foi validada para a UF " + uf + ".");
        return homologacao ? "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode"
                : "https://www.nfce.fazenda.sp.gov.br/qrcode";
    }

    public static String getNfceConsultaUrl(String uf, boolean homologacao) {
        if (!"SP".equalsIgnoreCase(uf))
            throw new IllegalStateException("URL de consulta NFC-e ainda não foi validada para a UF " + uf + ".");
        return homologacao ? "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta"
                : "https://www.nfce.fazenda.sp.gov.br/consulta";
    }

    @Getter
    @AllArgsConstructor
    public static class SefazUrls {
        private final String homologacao;
        private final String producao;
    }

    @Bean
    public NotaFiscalProperties notaFiscalProperties() {
        return new NotaFiscalProperties();
    }

    @Data
    @ConfigurationProperties(prefix = "nota-fiscal")
    public static class NotaFiscalProperties {
        private boolean homologacao = true;
        private String xmlStoragePath = "/var/erp/xmls";
        private String danfePath = "/var/erp/danfe";
        private int timeoutConexao = 30000;
        private int timeoutLeitura = 60000;
    }
}
