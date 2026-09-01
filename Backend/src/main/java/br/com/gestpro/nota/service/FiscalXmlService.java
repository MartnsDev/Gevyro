package br.com.gestpro.nota.service;

import br.com.gestpro.nota.config.FiscalSpecificationVersion;
import br.com.gestpro.nota.model.XmlFiscal;
import br.com.gestpro.nota.repository.XmlFiscalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class FiscalXmlService {
    private static final String NS = "http://www.portalfiscal.inf.br/nfe";
    private final XmlFiscalRepository repository;
    private final FiscalEncryptionService encryption;

    public String montarNfeProc(String xmlAssinado, String respostaSefaz) {
        try {
            Document nfe = parse(xmlAssinado);
            Document resposta = parse(respostaSefaz);
            NodeList protocolos = resposta.getElementsByTagNameNS("*", "protNFe");
            if (protocolos.getLength() != 1) throw new IllegalArgumentException("Resposta não contém um protocolo NF-e único.");
            Document saida = factory().newDocumentBuilder().newDocument();
            Element proc = saida.createElementNS(NS, "nfeProc");
            proc.setAttribute("versao", FiscalSpecificationVersion.NFE_LAYOUT);
            saida.appendChild(proc);
            proc.appendChild(saida.importNode(nfe.getDocumentElement(), true));
            proc.appendChild(saida.importNode(protocolos.item(0), true));
            return serialize(saida);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível preservar o XML autorizado com seu protocolo.", e);
        }
    }

    public void armazenarAutorizado(Long empresaId, Long documentoId, String nfeProc) {
        byte[] plain = nfeProc.getBytes(StandardCharsets.UTF_8);
        try {
            String hash = hex(sha256(plain));
            XmlFiscal existente = repository.findByDocumentoIdAndTipo(documentoId, XmlFiscal.Tipo.AUTORIZADO).orElse(null);
            if (existente != null) {
                if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                        existente.getSha256().getBytes(StandardCharsets.US_ASCII)))
                    throw new IllegalStateException("Tentativa de substituir XML fiscal imutável por conteúdo diferente.");
                return;
            }
            var encrypted = encryption.encrypt(plain);
            repository.save(new XmlFiscal(empresaId, documentoId, XmlFiscal.Tipo.AUTORIZADO,
                    encrypted.cipherText(), encrypted.nonce(), hash, FiscalSpecificationVersion.NFE_LAYOUT, "SEFAZ_DIRETO"));
        } finally { Arrays.fill(plain, (byte) 0); }
    }

    public byte[] carregarAutorizado(Long documentoId) {
        XmlFiscal xml = repository.findByDocumentoIdAndTipo(documentoId, XmlFiscal.Tipo.AUTORIZADO)
                .orElseThrow(() -> new IllegalStateException("XML autorizado não foi preservado."));
        byte[] plain = encryption.decrypt(xml.getConteudoCifrado(), xml.getNonce());
        byte[] esperado = xml.getSha256().getBytes(StandardCharsets.US_ASCII);
        byte[] atual = hex(sha256(plain)).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(esperado, atual)) {
            Arrays.fill(plain, (byte) 0);
            throw new SecurityException("Falha de integridade no XML fiscal armazenado.");
        }
        return plain;
    }

    private Document parse(String xml) throws Exception {
        if (xml == null || xml.length() > 5_000_000) throw new IllegalArgumentException("XML ausente ou excessivo.");
        var builder = factory().newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    private DocumentBuilderFactory factory() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setXIncludeAware(false); f.setExpandEntityReferences(false); return f;
    }
    private String serialize(Document document) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer t = tf.newTransformer(); t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter out = new StringWriter(); t.transform(new DOMSource(document), new StreamResult(out)); return out.toString();
    }
    private byte[] sha256(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private String hex(byte[] value) { return HexFormat.of().formatHex(value); }
}
