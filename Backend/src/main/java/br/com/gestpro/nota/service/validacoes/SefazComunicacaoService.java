package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.nota.config.NotaFiscalConfig;
import br.com.gestpro.nota.provider.FiscalProviderException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.net.ssl.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Set;

/**
 * A "ponte" entre o GestPro e os Webservices da Receita Estadual (SEFAZ).
 * A comunicação exige Autenticação Mútua (mTLS), ou seja, a SEFAZ verifica nosso
 * certificado e nós verificamos o deles durante o aperto de mão HTTPS (Handshake).
 */
@Slf4j // <-- Lombok: Substitui a declaração manual do Logger
@Service
@RequiredArgsConstructor
public class SefazComunicacaoService {

    private final AssinaturaDigitalService assinaturaDigitalService;
    private final FiscalXsdValidationService xsdValidationService;

    // A SEFAZ pode responder lentamente em períodos de pico.
    private static final int TIMEOUT_CONEXAO = 30_000;
    private static final int TIMEOUT_LEITURA = 60_000;

    /**
     * Pega o XML que já foi montado e assinado digitalmente, embala num envelope SOAP
     * e dispara pro Governo via HTTPS.
     *
     * @param xmlAssinado  XML final com a tag <Signature>
     * @param uf           Estado do remetente (define pra qual servidor vai)
     * @param modelo       "55" (NF-e) ou "65" (NFC-e)
     * @param pfxBytes     Arquivo do Certificado A1 (.pfx) em bytes
     * @param senhaCert    Senha do certificado (pra abrir o KeyStore)
     * @param homologacao  Se true, bate no servidor de testes sem validade jurídica
     */
    public RetornoSefaz enviarNfe(String xmlAssinado, String uf, String modelo,
                                  byte[] pfxBytes, String senhaCert, boolean homologacao) {
        try {
            String urlStr = NotaFiscalConfig.getWebserviceUrl(uf, modelo, homologacao);
            log.info("Disparando NF-e para a SEFAZ... URL: {} | Ambiente: {}", urlStr, homologacao ? "HOMOLOGAÇÃO (TESTE)" : "PRODUÇÃO (VALENDO!)");

            // A SEFAZ não lê o XML puro. Tem que empacotar dentro de uma requisição SOAP 1.2.
            String soapEnvelope = montarEnvelopeSoap(xmlAssinado, modelo);

            // Criando a conexão blindada. Se o certificado estiver vencido, falha aqui.
            HttpURLConnection conn = criarConexao(urlStr, pfxBytes, senhaCert);
            conn.setRequestMethod("POST");

            // Headers obrigatórios da SEFAZ
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4/nfeAutorizacaoLote");
            conn.setDoOutput(true);

            // Escreve o envelope no túnel da conexão
            try (OutputStream os = conn.getOutputStream()) {
                os.write(soapEnvelope.getBytes(StandardCharsets.UTF_8));
            }

            // Pega a resposta. Mesmo se der Erro 500 na SEFAZ, preciso ler o stream de erro pra saber o motivo.
            int httpCode = conn.getResponseCode();
            InputStream responseStream = httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String xmlRetorno = lerStream(responseStream);

            log.info("SEFAZ respondeu! HTTP Status: {} | Tamanho do retorno: {} bytes", httpCode, xmlRetorno.length());

            // Lê o XML devolvido pra saber se a nota foi Autorizada (100) ou Rejeitada.
            return parseRetornoSefaz(xmlRetorno);

        } catch (Exception e) {
            log.error("Falha de transporte na autorização fiscal; o resultado externo pode ser desconhecido.", e);
            throw new FiscalProviderException("Não foi possível confirmar o resultado da autorização fiscal.", true, e);
        }
    }

    /**
     * Envia um Evento de Cancelamento pra SEFAZ.
     * Cancelar é só enviar um mini-XML avisando que aquela nota não vale mais.
     */
    public RetornoSefaz cancelarNfe(String chaveAcesso, String protocolo, String justificativa,
                                    String uf, byte[] pfxBytes, String senhaCert, boolean homologacao) {
        try {
            // Monta o mini-XML de evento
            String xmlEvento = buildXmlCancelamento(chaveAcesso, protocolo, justificativa, homologacao);
            xmlEvento = assinaturaDigitalService.assinarEvento(xmlEvento, pfxBytes, senhaCert);

            // O endpoint de evento geralmente é diferente do de autorização
            String urlStr = NotaFiscalConfig.getWebserviceUrl(uf, "55", homologacao,
                    NotaFiscalConfig.SefazService.EVENTO);

            HttpURLConnection conn = criarConexao(urlStr, pfxBytes, senhaCert);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4/nfeRecepcaoEvento");
            conn.setDoOutput(true);

            String soap = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap12:Body><nfeRecepcaoEvento xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                    + "<nfeDadosMsg>" + xmlEvento + "</nfeDadosMsg>"
                    + "</nfeRecepcaoEvento></soap12:Body></soap12:Envelope>";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(soap.getBytes(StandardCharsets.UTF_8));
            }

            String xmlRetorno = lerStream(conn.getInputStream());
            return parseRetornoSefaz(xmlRetorno);

        } catch (Exception e) {
            log.error("Falha de transporte no evento fiscal; o resultado externo pode ser desconhecido.", e);
            throw new FiscalProviderException("Não foi possível confirmar o resultado do evento fiscal.", true, e);
        }
    }

    public RetornoSefaz enviarCartaCorrecao(String chaveAcesso, String correcao, int sequencia,
                                             String uf, byte[] pfxBytes, String senhaCert, boolean homologacao) {
        try {
            String xmlEvento = buildXmlCartaCorrecao(chaveAcesso, correcao, sequencia, homologacao);
            xmlEvento = assinaturaDigitalService.assinarEvento(xmlEvento, pfxBytes, senhaCert);
            xsdValidationService.validarCartaCorrecaoAssinada(xmlEvento);
            return enviarEvento(xmlEvento, uf, pfxBytes, senhaCert, homologacao);
        } catch (FiscalProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Falha de transporte na CC-e; o resultado externo pode ser desconhecido.", e);
            throw new FiscalProviderException("Não foi possível confirmar o resultado da CC-e.", true, e);
        }
    }

    private RetornoSefaz enviarEvento(String xmlEvento, String uf, byte[] pfxBytes,
                                      String senhaCert, boolean homologacao) throws Exception {
        String url = NotaFiscalConfig.getWebserviceUrl(uf, "55", homologacao, NotaFiscalConfig.SefazService.EVENTO);
        HttpURLConnection conn = criarConexao(url, pfxBytes, senhaCert);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
        conn.setRequestProperty("SOAPAction", "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4/nfeRecepcaoEvento");
        conn.setDoOutput(true);
        String soap = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>"
                + "<nfeRecepcaoEvento xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                + "<nfeDadosMsg>" + xmlEvento + "</nfeDadosMsg></nfeRecepcaoEvento></soap12:Body></soap12:Envelope>";
        try (OutputStream os = conn.getOutputStream()) { os.write(soap.getBytes(StandardCharsets.UTF_8)); }
        int httpCode = conn.getResponseCode();
        InputStream stream = httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (httpCode >= 500) throw new IOException("Autorizador indisponível (HTTP " + httpCode + ").");
        return parseRetornoSefaz(lerStream(stream));
    }

    public RetornoSefaz consultarSituacao(String chaveAcesso, String uf, byte[] pfxBytes,
                                           String senhaCert, boolean homologacao) {
        if (chaveAcesso == null || !chaveAcesso.matches("[0-9]{44}"))
            throw new IllegalArgumentException("Chave de acesso inválida para consulta fiscal.");
        try {
            String url = NotaFiscalConfig.getWebserviceUrl(uf, "55", homologacao,
                    NotaFiscalConfig.SefazService.CONSULTA_PROTOCOLO);
            int tpAmb = homologacao ? 2 : 1;
            String mensagem = "<consSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                    + "<tpAmb>" + tpAmb + "</tpAmb><xServ>CONSULTAR</xServ><chNFe>" + chaveAcesso
                    + "</chNFe></consSitNFe>";
            String soap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>"
                    + "<nfeConsultaNF xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4\">"
                    + "<nfeDadosMsg>" + mensagem + "</nfeDadosMsg></nfeConsultaNF></soap12:Body></soap12:Envelope>";
            HttpURLConnection conn = criarConexao(url, pfxBytes, senhaCert);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", "http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4/nfeConsultaNF");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) { os.write(soap.getBytes(StandardCharsets.UTF_8)); }
            int httpCode = conn.getResponseCode();
            InputStream stream = httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (httpCode >= 500) throw new IOException("Autorizador indisponível (HTTP " + httpCode + ").");
            return parseRetornoSefaz(lerStream(stream));
        } catch (Exception e) {
            if (e instanceof FiscalProviderException fiscal) throw fiscal;
            throw new FiscalProviderException("Não foi possível consultar a situação fiscal.", false, e);
        }
    }

    public RetornoSefaz inutilizarNumeracao(String cnpj, String uf, String modelo, int ano, int serie,
                                             long inicio, long fim, String justificativa,
                                             byte[] pfxBytes, String senhaCert, boolean homologacao) {
        try {
            String xml = buildXmlInutilizacao(cnpj, uf, modelo, ano, serie, inicio, fim, justificativa, homologacao);
            String assinado = assinaturaDigitalService.assinarInutilizacao(xml, pfxBytes, senhaCert);
            xsdValidationService.validarInutilizacaoAssinada(assinado);
            String url = NotaFiscalConfig.getWebserviceUrl(uf, modelo, homologacao,
                    NotaFiscalConfig.SefazService.INUTILIZACAO);
            String soap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>"
                    + "<nfeInutilizacaoNF xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4\">"
                    + "<nfeDadosMsg>" + assinado + "</nfeDadosMsg></nfeInutilizacaoNF></soap12:Body></soap12:Envelope>";
            HttpURLConnection conn = criarConexao(url, pfxBytes, senhaCert);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", "http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4/nfeInutilizacaoNF");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) { os.write(soap.getBytes(StandardCharsets.UTF_8)); }
            int httpCode = conn.getResponseCode();
            InputStream stream = httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (httpCode >= 500) throw new IOException("Autorizador indisponível (HTTP " + httpCode + ").");
            return parseRetornoSefaz(lerStream(stream));
        } catch (Exception e) {
            if (e instanceof FiscalProviderException fiscal) throw fiscal;
            throw new FiscalProviderException("Não foi possível confirmar a inutilização fiscal.", true, e);
        }
    }

    String buildXmlInutilizacao(String cnpj, String uf, String modelo, int ano, int serie,
                                long inicio, long fim, String justificativa, boolean homologacao) {
        String documento = cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        if (!documento.matches("[0-9]{14}")) throw new IllegalArgumentException("CNPJ inválido para inutilização.");
        if (!modelo.matches("55|65")) throw new IllegalArgumentException("Modelo fiscal inválido para inutilização.");
        if (ano < 2006 || ano > java.time.Year.now().getValue()) throw new IllegalArgumentException("Ano inválido para inutilização.");
        if (serie < 0 || serie > 999 || inicio < 1 || fim < inicio || fim > 999999999L)
            throw new IllegalArgumentException("Série ou faixa inválida para inutilização.");
        String motivo = justificativa == null ? "" : justificativa.trim();
        if (motivo.length() < 15 || motivo.length() > 255) throw new IllegalArgumentException("Justificativa inválida para inutilização.");
        String cUf = GerarChaveAcesso.getCodigoUf(uf);
        String anoCurto = String.format("%02d", ano % 100);
        String id = "ID" + cUf + anoCurto + documento + modelo + String.format("%03d%09d%09d", serie, inicio, fim);
        return "<inutNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><infInut Id=\"" + id + "\">"
                + "<tpAmb>" + (homologacao ? 2 : 1) + "</tpAmb><xServ>INUTILIZAR</xServ><cUF>" + cUf + "</cUF>"
                + "<ano>" + anoCurto + "</ano><CNPJ>" + documento + "</CNPJ><mod>" + modelo + "</mod>"
                + "<serie>" + serie + "</serie><nNFIni>" + inicio + "</nNFIni><nNFFin>" + fim + "</nNFFin>"
                + "<xJust>" + escapeXml(motivo) + "</xJust></infInut></inutNFe>";
    }

    /**
     * Empacota a NF-e nua dentro de um envelope SOAP padrão do Governo.
     */
    private String montarEnvelopeSoap(String xmlNfe, String modelo) {
        String wsdlPath = "NFeAutorizacao4"; // Pra NFC-e pode mudar no futuro dependendo do estado
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>"
                + "<nfeAutorizacaoLote xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/" + wsdlPath + "\">"
                + "<nfeDadosMsg>" + xmlNfe + "</nfeDadosMsg>"
                + "</nfeAutorizacaoLote>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }

    /**
     * Monta o evento 110111 (Cancelamento).
     */
    String buildXmlCancelamento(String chaveAcesso, String protocolo,
                                String justificativa, boolean homologacao) {
        if (chaveAcesso == null || !chaveAcesso.matches("[0-9]{44}")) {
            throw new IllegalArgumentException("Chave de acesso inválida para cancelamento.");
        }
        if (protocolo == null || !protocolo.matches("[0-9]{15}")) {
            throw new IllegalArgumentException("Protocolo de autorização inválido para cancelamento.");
        }
        String motivo = justificativa == null ? "" : justificativa.trim();
        if (motivo.length() < 15 || motivo.length() > 255 || motivo.chars().anyMatch(c -> c < 0x20 && c != '\t' && c != '\n' && c != '\r')) {
            throw new IllegalArgumentException("Justificativa inválida para cancelamento.");
        }
        int tpAmb = homologacao ? 2 : 1;
        String now = java.time.OffsetDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return "<envEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<idLote>1</idLote>"
                + "<evento versao=\"1.00\">"
                + "<infEvento Id=\"ID110111" + chaveAcesso + "01\">"
                + "<cOrgao>" + chaveAcesso.substring(0, 2) + "</cOrgao>" // UF tirada do início da chave
                + "<tpAmb>" + tpAmb + "</tpAmb>"
                + "<CNPJ>" + chaveAcesso.substring(6, 20) + "</CNPJ>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhEvento>" + now + "</dhEvento>"
                + "<tpEvento>110111</tpEvento>"
                + "<nSeqEvento>1</nSeqEvento>"
                + "<verEvento>1.00</verEvento>"
                + "<detEvento versao=\"1.00\">"
                + "<descEvento>Cancelamento</descEvento>"
                + "<nProt>" + protocolo + "</nProt>"
                + "<xJust>" + escapeXml(motivo) + "</xJust>"
                + "</detEvento>"
                + "</infEvento>"
                + "</evento>"
                + "</envEvento>";
    }

    static final String CONDICOES_USO_CCE = "A Carta de Correção é disciplinada pelo § 1º-A do art. 7º do Convênio S/N, de 15 de dezembro de 1970 e pode ser utilizada para regularização de erro ocorrido na emissão de documento fiscal, desde que o erro não esteja relacionado com: I - as variáveis que determinam o valor do imposto tais como: base de cálculo, alíquota, diferença de preço, quantidade, valor da operação ou da prestação; II - a correção de dados cadastrais que implique mudança do remetente ou do destinatário; III - a data de emissão ou de saída.";

    String buildXmlCartaCorrecao(String chaveAcesso, String correcao, int sequencia, boolean homologacao) {
        if (chaveAcesso == null || !chaveAcesso.matches("[0-9]{44}")) throw new IllegalArgumentException("Chave de acesso inválida para CC-e.");
        String texto = correcao == null ? "" : correcao.trim();
        if (texto.length() < 15 || texto.length() > 1000 || texto.chars().anyMatch(c -> c < 0x20))
            throw new IllegalArgumentException("Correção deve conter entre 15 e 1000 caracteres válidos.");
        if (sequencia < 1 || sequencia > 20) throw new IllegalArgumentException("A CC-e admite sequências de 1 a 20.");
        String agora = java.time.OffsetDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String seqId = String.format("%02d", sequencia);
        return "<envEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\"><idLote>1</idLote>"
                + "<evento versao=\"1.00\"><infEvento Id=\"ID110110" + chaveAcesso + seqId + "\">"
                + "<cOrgao>" + chaveAcesso.substring(0, 2) + "</cOrgao><tpAmb>" + (homologacao ? 2 : 1) + "</tpAmb>"
                + "<CNPJ>" + chaveAcesso.substring(6, 20) + "</CNPJ><chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhEvento>" + agora + "</dhEvento><tpEvento>110110</tpEvento><nSeqEvento>" + sequencia + "</nSeqEvento>"
                + "<verEvento>1.00</verEvento><detEvento versao=\"1.00\"><descEvento>Carta de Correcao</descEvento>"
                + "<xCorrecao>" + escapeXml(texto) + "</xCorrecao><xCondUso>" + escapeXml(CONDICOES_USO_CCE)
                + "</xCondUso></detEvento></infEvento></evento></envEvento>";
    }

    private String escapeXml(String valor) {
        return valor.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * O núcleo da segurança. Configura a conexão pra apresentar nosso certificado pra SEFAZ.
     */
    private HttpURLConnection criarConexao(String urlStr, byte[] pfxBytes, String senhaCert) throws Exception {
        // Carrega nosso certificado (identidade do cliente)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new ByteArrayInputStream(pfxBytes)) {
            keyStore.load(is, senhaCert.toCharArray());
        }

        // KeyManagerFactory é quem vai "mostrar a carteira de identidade" pra SEFAZ
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, senhaCert.toCharArray());

        // TrustManager define em quem nós confiamos.
        // Dica de Produção: É aqui que carregamos os "Cadeados da ICP-Brasil".
        // Passar nulo confia no padrão do Java, mas pra SEFAZ às vezes dá erro de cadeia.
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        // A SEFAZ só aceita TLS 1.2 pra cima!
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        conn.setConnectTimeout(TIMEOUT_CONEXAO);
        conn.setReadTimeout(TIMEOUT_LEITURA);
        return conn;
    }

    /**
     * Extrai os campos necessários da resposta da SEFAZ.
     * Cód Status (100 é sucesso), Motivo da Rejeição, Recibo, etc.
     */
    RetornoSefaz parseRetornoSefaz(String xmlRetorno) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xmlRetorno.getBytes(StandardCharsets.UTF_8)));

            String cStat = getTagValue(doc, "cStat");
            String xMotivo = getTagValue(doc, "xMotivo");
            String nProt = getTagValue(doc, "nProt");
            String dhRecbto = getTagValue(doc, "dhRecbto");

            RetornoSefaz retorno = new RetornoSefaz();
            retorno.setXmlRetorno(xmlRetorno);
            retorno.setCodigo(cStat);
            retorno.setMensagem(xMotivo);
            retorno.setProtocolo(nProt);
            retorno.setDataHoraRecebimento(dhRecbto);
            // Em respostas compostas, getTagValue seleciona o resultado interno
            // (protNFe/retEvento), nunca o mero "lote processado" externo (104/128).
            retorno.setSucesso(Set.of("100", "102", "135", "136", "155").contains(cStat));

            return retorno;
        } catch (Exception e) {
            log.error("Resposta fiscal inválida ou incompleta recebida do autorizador.", e);
            throw new FiscalProviderException("O autorizador fiscal retornou uma resposta inválida.", true, e);
        }
    }

    /**
     * Busca rápida de valor de tag dentro do XML.
     */
    private String getTagValue(Document doc, String tagName) {
        NodeList nl = doc.getElementsByTagNameNS("*", tagName);
        if (nl != null && nl.getLength() > 0) {
            return nl.item(nl.getLength() - 1).getTextContent();
        }
        return null;
    }

    /**
     * Transforma o fluxo de bits (InputStream) que veio da rede numa String XML limpinha.
     */
    private String lerStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    // DTO Local de Resposta (Refatorado com Lombok)
    @Data
    public static class RetornoSefaz {
        private boolean sucesso;
        private String codigo;
        private String mensagem;
        private String protocolo;
        private String dataHoraRecebimento;
        private String xmlRetorno;

        public static RetornoSefaz erro(String mensagem) {
            RetornoSefaz r = new RetornoSefaz();
            r.sucesso = false;
            r.mensagem = mensagem;
            r.codigo = "999"; // Código genérico pra erro interno da aplicação
            return r;
        }
    }
}
