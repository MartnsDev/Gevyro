package br.com.gestpro.nota.service.validacoes;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class AssinaturaDigitalService {

    public String assinarXml(String xmlConteudo, byte[] pfxBytes, String senhaCert) throws Exception {
        return assinarElemento(xmlConteudo, pfxBytes, senhaCert, "infNFe");
    }

    public String assinarEvento(String xmlConteudo, byte[] pfxBytes, String senhaCert) throws Exception {
        return assinarElemento(xmlConteudo, pfxBytes, senhaCert, "infEvento");
    }

    private String assinarElemento(String xmlConteudo, byte[] pfxBytes, String senhaCert,
                                   String elementoLocal) throws Exception {
        // Carrega o certificado do KeyStore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new ByteArrayInputStream(pfxBytes)) {
            keyStore.load(is, senhaCert.toCharArray());
        }

        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, senhaCert.toCharArray());
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);

        // Parse do XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc;
        try (InputStream is = new ByteArrayInputStream(xmlConteudo.getBytes("UTF-8"))) {
            doc = db.parse(is);
        }

        NodeList elementos = doc.getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", elementoLocal);
        if (elementos.getLength() != 1) {
            throw new IllegalArgumentException("XML fiscal deve possuir exatamente um elemento " + elementoLocal + ".");
        }
        Element elementoParaAssinar = (Element) elementos.item(0);
        String id = elementoParaAssinar.getAttribute("Id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Elemento fiscal sem atributo Id para assinatura.");
        }
        elementoParaAssinar.setIdAttribute("Id", true);

        // Configura o motor de assinatura XML
        XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance("DOM");

        // Digest Method: SHA-256
        DigestMethod digestMethod = signatureFactory.newDigestMethod(DigestMethod.SHA256, null);

        // Transforms: Enveloped Signature + C14N
        List<Transform> transforms = new ArrayList<>();
        transforms.add(signatureFactory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null));
        transforms.add(signatureFactory.newTransform("http://www.w3.org/TR/2001/REC-xml-c14n-20010315", (TransformParameterSpec) null));

        Reference reference = signatureFactory.newReference(
                "#" + id,
                digestMethod,
                transforms,
                null,
                null
        );

        // Signed Info: C14N + RSA-SHA256
        SignedInfo signedInfo = signatureFactory.newSignedInfo(
                signatureFactory.newCanonicalizationMethod(
                        CanonicalizationMethod.INCLUSIVE,
                        (C14NMethodParameterSpec) null
                ),
                signatureFactory.newSignatureMethod(
                        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
                        null
                ),
                Collections.singletonList(reference)
        );

        // KeyInfo com o certificado X.509
        KeyInfoFactory keyInfoFactory = signatureFactory.getKeyInfoFactory();
        X509Data x509Data = keyInfoFactory.newX509Data(Collections.singletonList(certificate));
        KeyInfo keyInfo = keyInfoFactory.newKeyInfo(Collections.singletonList(x509Data));

        // Contexto de assinatura - insere após o elemento infNFe
        DOMSignContext domSignContext = new DOMSignContext(privateKey, elementoParaAssinar.getParentNode());
        domSignContext.setNextSibling(elementoParaAssinar.getNextSibling());

        // Cria e executa a assinatura
        XMLSignature signature = signatureFactory.newXMLSignature(signedInfo, keyInfo);
        signature.sign(domSignContext);

        // Serializa o documento assinado
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");

        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    public boolean isCertificadoValido(byte[] pfxBytes, String senhaCert) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new ByteArrayInputStream(pfxBytes)) {
                keyStore.load(is, senhaCert.toCharArray());
            }
            String alias = keyStore.aliases().nextElement();
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            cert.checkValidity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public java.util.Map<String, String> getInfoCertificado(byte[] pfxBytes, String senhaCert) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new ByteArrayInputStream(pfxBytes)) {
            keyStore.load(is, senhaCert.toCharArray());
        }
        String alias = keyStore.aliases().nextElement();
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("titular", cert.getSubjectX500Principal().getName());
        info.put("emissor", cert.getIssuerX500Principal().getName());
        info.put("validoAte", cert.getNotAfter().toString());
        info.put("valido", String.valueOf(isCertificadoValido(pfxBytes, senhaCert)));
        info.put("serialNumber", cert.getSerialNumber().toString());
        return info;
    }
}
