package br.com.gestpro.nota.service;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.config.FiscalSpecificationVersion;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.nota.service.validacoes.FiscalXsdValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class NfeXmlImportService {
    public static final int MAX_XML_BYTES = 2 * 1024 * 1024;
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String DS_NS = XMLSignature.XMLNS;
    private final EmpresaRepository empresas;
    private final NotaFiscalRepository notas;
    private final FiscalXsdValidationService xsd;
    private final FiscalXmlService xmlStorage;

    @Transactional
    public NotaFiscal importar(Long empresaId, byte[] conteudo) {
        Empresa empresa = empresas.findById(empresaId)
                .orElseThrow(() -> erro("Empresa não encontrada."));
        if (conteudo == null || conteudo.length == 0 || conteudo.length > MAX_XML_BYTES)
            throw erro("O XML deve possuir entre 1 byte e 2 MiB.");
        String xmlOriginal = utf8Estrito(conteudo);
        Document doc = parseSeguro(conteudo);
        Element proc = raiz(doc, "nfeProc");
        Element nfe = unico(proc, NFE_NS, "NFe");
        Element infNfe = unico(nfe, NFE_NS, "infNFe");
        Element infProt = unico(unico(proc, NFE_NS, "protNFe"), NFE_NS, "infProt");

        String chave = texto(infProt, "chNFe");
        if (!chave.matches("\\d{44}") || !("NFe" + chave).equals(infNfe.getAttribute("Id")))
            throw erro("A chave do protocolo não corresponde à NF-e assinada.");
        String status = texto(infProt, "cStat");
        if (!status.equals("100") && !status.equals("150"))
            throw erro("O protocolo não comprova autorização da NF-e.");
        String emitente = texto(unico(infNfe, NFE_NS, "emit"), "CNPJ");
        String cnpjEmpresa = digitos(empresa.getCnpj());
        if (!emitente.equals(cnpjEmpresa))
            throw erro("O CNPJ emitente do XML não pertence à empresa selecionada.");

        byte[] digestAssinado = validarAssinatura(nfe, infNfe);
        byte[] digestProtocolo;
        try { digestProtocolo = java.util.Base64.getDecoder().decode(texto(infProt, "digVal")); }
        catch (IllegalArgumentException e) { throw erro("Digest do protocolo fiscal inválido."); }
        if (!MessageDigest.isEqual(digestAssinado, digestProtocolo))
            throw erro("O protocolo fiscal não corresponde ao digest da NF-e assinada.");
        xsd.validarNfeAssinada(serializar(nfe));

        NotaFiscal existente = notas.findByChaveAcesso(chave).orElse(null);
        if (existente != null) {
            if (!existente.getEmpresaId().equals(empresaId)) throw erro("A chave já está vinculada a outra empresa.");
            return existente;
        }

        Element ide = unico(infNfe, NFE_NS, "ide");
        String modelo = texto(ide, "mod");
        TipoNota tipo = switch (modelo) { case "55" -> TipoNota.NFE; case "65" -> TipoNota.NFCE;
            default -> throw erro("Somente NF-e modelo 55 e NFC-e modelo 65 podem ser importadas."); };
        Element total = unico(unico(infNfe, NFE_NS, "total"), NFE_NS, "ICMSTot");
        Element destinatario = opcional(infNfe, "dest");
        NotaFiscal nota = NotaFiscal.builder()
                .empresaId(empresaId).tipo(tipo).status(NotaFiscalStatus.AUTORIZADA)
                .numeroNota(longValor(texto(ide, "nNF"))).serie(texto(ide, "serie"))
                .chaveAcesso(chave).naturezaOperacao(texto(ide, "natOp"))
                .clienteNome(destinatario == null ? null : textoOpcional(destinatario, "xNome"))
                .clienteCpfCnpj(destinatario == null ? null : primeiro(textoOpcional(destinatario, "CNPJ"), textoOpcional(destinatario, "CPF")))
                .valorProdutos(decimal(total, "vProd")).valorFrete(decimal(total, "vFrete"))
                .valorDesconto(decimal(total, "vDesc")).valorIcms(decimal(total, "vICMS"))
                .valorPis(decimal(total, "vPIS")).valorCofins(decimal(total, "vCOFINS"))
                .valorTotal(decimal(total, "vNF")).dataEmissao(data(texto(ide, "dhEmi")))
                .dataAutorizacao(data(texto(infProt, "dhRecbto"))).protocolo(texto(infProt, "nProt"))
                .build();
        nota = notas.save(nota);
        xmlStorage.armazenarAutorizado(empresaId, nota.getId(), xmlOriginal,
                FiscalSpecificationVersion.NFE_LAYOUT, "XML_IMPORTADO");
        return nota;
    }

    private Document parseSeguro(byte[] xml) {
        try {
            var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) { throw erro("XML malformado ou com construção insegura."); }
    }

    private String utf8Estrito(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { throw erro("O XML deve utilizar codificação UTF-8 válida."); }
    }

    private byte[] validarAssinatura(Element nfe, Element infNfe) {
        try {
            NodeList assinaturas = nfe.getElementsByTagNameNS(DS_NS, "Signature");
            if (assinaturas.getLength() != 1) throw erro("A NF-e deve possuir exatamente uma assinatura digital.");
            infNfe.setIdAttribute("Id", true);
            DOMValidateContext contexto = new DOMValidateContext(new CertificadoKeySelector(), assinaturas.item(0));
            contexto.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            XMLSignature assinatura = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto);
            @SuppressWarnings("unchecked") List<Reference> referencias = assinatura.getSignedInfo().getReferences();
            if (referencias.size() != 1 || !("#" + infNfe.getAttribute("Id")).equals(referencias.get(0).getURI())
                    || !assinatura.validate(contexto)) throw erro("A assinatura digital da NF-e é inválida.");
            return referencias.get(0).getDigestValue().clone();
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw erro("Não foi possível validar a assinatura digital da NF-e."); }
    }

    private String serializar(Element element) {
        try {
            TransformerFactory f = TransformerFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var t = f.newTransformer(); t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter out = new StringWriter(); t.transform(new DOMSource(element), new StreamResult(out)); return out.toString();
        } catch (Exception e) { throw erro("Não foi possível validar a estrutura da NF-e."); }
    }

    private Element raiz(Document doc, String nome) { Element e = doc.getDocumentElement();
        if (e == null || !NFE_NS.equals(e.getNamespaceURI()) || !nome.equals(e.getLocalName())) throw erro("O arquivo não é um nfeProc autorizado."); return e; }
    private Element unico(Element pai, String ns, String nome) { NodeList n = pai.getElementsByTagNameNS(ns, nome);
        if (n.getLength() != 1) throw erro("Estrutura fiscal inválida: " + nome + " deve ocorrer uma vez."); return (Element) n.item(0); }
    private Element opcional(Element pai, String nome) { NodeList n = pai.getElementsByTagNameNS(NFE_NS, nome); return n.getLength() == 0 ? null : (Element) n.item(0); }
    private String texto(Element pai, String nome) { String v = textoOpcional(pai, nome); if (v == null || v.isBlank()) throw erro("Campo obrigatório ausente: " + nome + "."); return v.trim(); }
    private String textoOpcional(Element pai, String nome) { NodeList n = pai.getElementsByTagNameNS(NFE_NS, nome); return n.getLength() == 0 ? null : n.item(0).getTextContent().trim(); }
    private BigDecimal decimal(Element pai, String nome) { try { return new BigDecimal(texto(pai, nome)); } catch (NumberFormatException e) { throw erro("Valor fiscal inválido: " + nome + "."); } }
    private Long longValor(String v) { try { return Long.valueOf(v); } catch (NumberFormatException e) { throw erro("Número fiscal inválido."); } }
    private java.time.LocalDateTime data(String v) { try { return OffsetDateTime.parse(v).toLocalDateTime(); } catch (Exception e) { throw erro("Data fiscal inválida."); } }
    private String digitos(String v) { return v == null ? "" : v.replaceAll("\\D", ""); }
    private String primeiro(String a, String b) { return a != null && !a.isBlank() ? a : b; }
    private ApiException erro(String mensagem) { return new ApiException(mensagem, HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/importar-xml"); }

    private static final class CertificadoKeySelector extends KeySelector {
        @Override public KeySelectorResult select(javax.xml.crypto.dsig.keyinfo.KeyInfo info, Purpose purpose,
                                                   AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException {
            if (info == null) throw new KeySelectorException("KeyInfo ausente");
            for (Object item : info.getContent()) if (item instanceof X509Data dados)
                for (Object valor : dados.getContent()) if (valor instanceof X509Certificate cert) {
                    PublicKey key = cert.getPublicKey();
                    if (algoritmoCompativel(method.getAlgorithm(), key.getAlgorithm())) return () -> key;
                }
            throw new KeySelectorException("Certificado X.509 ausente");
        }
        private boolean algoritmoCompativel(String assinatura, String chave) {
            return assinatura != null && ((assinatura.contains("rsa") && "RSA".equalsIgnoreCase(chave))
                    || (assinatura.contains("ecdsa") && "EC".equalsIgnoreCase(chave)));
        }
    }
}
