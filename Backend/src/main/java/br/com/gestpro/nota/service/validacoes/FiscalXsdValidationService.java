package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.config.FiscalSpecificationVersion;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Valida NF-e/NFC-e exclusivamente contra o pacote XSD oficial embarcado. */
@Service
public class FiscalXsdValidationService {

    static final int MAX_XML_BYTES = 2 * 1024 * 1024;
    private static final String BASE = "/fiscal/nfe/PL_010f_v1.04/";
    private static final String BASE_INUTILIZACAO = "/fiscal/nfe/inutilizacao_v4.00/";
    private static final String BASE_CCE = "/fiscal/nfe/cce_v1.00/";
    private static final String ENTRYPOINT = "nfe_v4.00.xsd";

    private Schema schemaNfe;
    private Schema schemaInutilizacao;
    private Schema schemaCce;

    @PostConstruct
    void carregarSchema() {
        try {
            schemaNfe = compilarSchema(BASE, ENTRYPOINT);
            schemaInutilizacao = compilarSchema(BASE_INUTILIZACAO, "inutNFe_v4.00.xsd");
            schemaCce = compilarSchema(BASE_CCE, "envCCe_v1.00.xsd");
        } catch (Exception erro) {
            throw new IllegalStateException("Pacote XSD fiscal oficial indisponível ou inválido: "
                    + FiscalSpecificationVersion.NFE_SCHEMA_PACKAGE, erro);
        }
    }

    private Schema compilarSchema(String base, String entrypoint) throws Exception {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setResourceResolver(new ClasspathSchemaResolver(base));
            try (InputStream entrada = recurso(base, entrypoint)) {
                StreamSource source = new StreamSource(entrada);
                source.setSystemId("classpath:" + base + entrypoint);
                return factory.newSchema(source);
            }
    }

    public void validarNfeAssinada(String xml) {
        validar(xml, schemaNfe, FiscalSpecificationVersion.NFE_SCHEMA_PACKAGE);
    }

    public void validarInutilizacaoAssinada(String xml) {
        validar(xml, schemaInutilizacao, "inutNFe_v4.00");
    }

    public void validarCartaCorrecaoAssinada(String xml) {
        validar(xml, schemaCce, "Evento_CCe_PL_v1.01");
    }

    private void validar(String xml, Schema schema, String versao) {
        if (xml == null || xml.isBlank()) {
            throw xmlInvalido("XML fiscal vazio");
        }
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_XML_BYTES) {
            throw xmlInvalido("XML fiscal excede o limite de 2 MiB");
        }
        try {
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new ByteArrayInputStream(bytes)));
        } catch (SAXException erro) {
            throw xmlInvalido("XML incompatível com o XSD "
                    + versao + formatarLocal(erro));
        } catch (Exception erro) {
            throw new ApiException("Não foi possível validar o XML fiscal com segurança.",
                    HttpStatus.INTERNAL_SERVER_ERROR, "/api/nota-fiscal/emitir");
        }
    }

    private String formatarLocal(SAXException erro) {
        if (erro instanceof org.xml.sax.SAXParseException parse) {
            return " (linha " + parse.getLineNumber() + ", coluna " + parse.getColumnNumber() + ")";
        }
        return "";
    }

    private ApiException xmlInvalido(String detalhe) {
        return new ApiException(detalhe + ". O documento não foi enviado à SEFAZ.",
                HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/emitir");
    }

    private InputStream recurso(String base, String nome) {
        if (nome == null || nome.contains("/") || nome.contains("\\") || nome.contains("..")) {
            throw new IllegalArgumentException("Referência XSD inválida");
        }
        InputStream stream = FiscalXsdValidationService.class.getResourceAsStream(base + nome);
        if (stream == null) throw new IllegalStateException("XSD ausente: " + nome);
        return stream;
    }

    private final class ClasspathSchemaResolver implements LSResourceResolver {
        private final String base;
        private ClasspathSchemaResolver(String base) { this.base = base; }
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId,
                                       String systemId, String baseURI) {
            if (systemId == null) return null;
            return new LocalLsInput(publicId, systemId, recurso(base, systemId));
        }
    }

    private static final class LocalLsInput implements LSInput {
        private final String publicId;
        private final String systemId;
        private InputStream byteStream;

        private LocalLsInput(String publicId, String systemId, InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.byteStream = byteStream;
        }

        public InputStream getByteStream() { return byteStream; }
        public void setByteStream(InputStream value) { byteStream = value; }
        public String getPublicId() { return publicId; }
        public String getSystemId() { return systemId; }
        public String getBaseURI() { return null; }
        public String getEncoding() { return StandardCharsets.UTF_8.name(); }
        public boolean getCertifiedText() { return false; }
        public java.io.Reader getCharacterStream() { return null; }
        public String getStringData() { return null; }
        public void setCharacterStream(java.io.Reader value) { }
        public void setStringData(String value) { }
        public void setSystemId(String value) { }
        public void setPublicId(String value) { }
        public void setBaseURI(String value) { }
        public void setEncoding(String value) { }
        public void setCertifiedText(boolean value) { }
    }
}
