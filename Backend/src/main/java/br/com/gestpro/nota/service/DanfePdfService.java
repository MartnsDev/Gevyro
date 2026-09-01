package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Gera o DANFE retrato a partir do XML autorizado, que permanece a fonte de verdade. */
@Service
@RequiredArgsConstructor
public class DanfePdfService {
    private static final float M = 24;
    private static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final NotaFiscalRepository notaRepository;
    private final FiscalXmlService fiscalXmlService;

    public byte[] gerar(Long notaId) {
        NotaFiscal nota = notaRepository.findById(notaId).orElseThrow(() -> new ApiException(
                "Nota fiscal não encontrada.", HttpStatus.NOT_FOUND, "/api/nota-fiscal/danfe"));
        if (nota.getTipo() != TipoNota.NFE) throw new ApiException(
                "Este gerador atende somente DANFE da NF-e modelo 55.", HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/danfe");
        if (nota.getStatus() != NotaFiscalStatus.AUTORIZADA && nota.getStatus() != NotaFiscalStatus.CANCELADA)
            throw new ApiException("O DANFE exige XML autorizado.", HttpStatus.CONFLICT, "/api/nota-fiscal/danfe");

        byte[] xmlOriginal = fiscalXmlService.carregarAutorizado(notaId);
        Document xml = parseSeguro(xmlOriginal);
        String chave = texto(xml, "chNFe");
        if (chave.isBlank()) {
            Element inf = primeiro(xml, "infNFe");
            chave = inf == null ? "" : inf.getAttribute("Id").replaceFirst("^NFe", "");
        }
        if (!chave.matches("[0-9]{44}")) throw new ApiException(
                "XML autorizado sem chave de acesso válida.", HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/danfe");

        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<Element> itens = elementos(xml, "det");
            int indice = 0;
            do {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    float y = cabecalho(cs, xml, chave, nota.getStatus());
                    y = secoesPrincipais(cs, xml, y);
                    y = cabecalhoItens(cs, y);
                    while (indice < itens.size() && y > 105) {
                        y = item(cs, itens.get(indice++), y);
                    }
                    if (indice >= itens.size()) rodape(cs, xml, y);
                    texto(cs, "DANFE - Documento Auxiliar da Nota Fiscal Eletrônica", M, 14, 6, NORMAL);
                    texto(cs, "Página " + pdf.getNumberOfPages(), 530, 14, 6, NORMAL);
                }
            } while (indice < itens.size());
            pdf.save(out);
            return out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Não foi possível gerar o DANFE a partir do XML autorizado.",
                    HttpStatus.INTERNAL_SERVER_ERROR, "/api/nota-fiscal/danfe");
        }
    }

    private float cabecalho(PDPageContentStream cs, Document xml, String chave, NotaFiscalStatus status) throws Exception {
        float y = 812;
        caixa(cs, M, y - 92, 547, 92);
        texto(cs, limitar(texto(xml, "xNome"), 55), M + 8, y - 20, 11, BOLD);
        texto(cs, "DANFE", 260, y - 22, 16, BOLD);
        texto(cs, "Documento Auxiliar da NF-e", 238, y - 36, 7, NORMAL);
        texto(cs, "0-ENTRADA  1-SAÍDA: " + texto(xml, "tpNF"), 238, y - 49, 7, BOLD);
        texto(cs, "Nº " + texto(xml, "nNF") + "   SÉRIE " + texto(xml, "serie"), 238, y - 64, 9, BOLD);
        codigoBarras(cs, chave, 390, y - 50, 174, 34);
        texto(cs, agruparChave(chave), 391, y - 62, 7, NORMAL);
        texto(cs, "CHAVE DE ACESSO", 391, y - 73, 6, BOLD);
        texto(cs, "Protocolo: " + texto(xml, "nProt") + "  " + texto(xml, "dhRecbto"), 391, y - 85, 6, NORMAL);
        if (status == NotaFiscalStatus.CANCELADA) texto(cs, "DOCUMENTO CANCELADO", 205, y - 112, 18, BOLD);
        return y - 120;
    }

    private float secoesPrincipais(PDPageContentStream cs, Document xml, float y) throws Exception {
        caixa(cs, M, y - 34, 547, 34);
        rotuloValor(cs, "NATUREZA DA OPERAÇÃO", texto(xml, "natOp"), M + 4, y - 8);
        rotuloValor(cs, "CNPJ", texto(xml, "CNPJ"), 350, y - 8);
        rotuloValor(cs, "INSCRIÇÃO ESTADUAL", texto(xml, "IE"), 455, y - 8);
        y -= 40;
        Element dest = primeiro(xml, "dest");
        caixa(cs, M, y - 40, 547, 40);
        rotuloValor(cs, "DESTINATÁRIO / REMETENTE", filho(dest, "xNome"), M + 4, y - 8);
        rotuloValor(cs, "CPF/CNPJ", primeiroNaoVazio(filho(dest, "CNPJ"), filho(dest, "CPF")), 380, y - 8);
        rotuloValor(cs, "DATA DE EMISSÃO", texto(xml, "dhEmi"), M + 4, y - 27);
        rotuloValor(cs, "ENDEREÇO", endereco(dest), 220, y - 27);
        y -= 46;
        caixa(cs, M, y - 35, 547, 35);
        rotuloValor(cs, "BASE ICMS", texto(xml, "vBC"), M + 4, y - 8);
        rotuloValor(cs, "VALOR ICMS", texto(xml, "vICMS"), 110, y - 8);
        rotuloValor(cs, "VALOR PRODUTOS", texto(xml, "vProd"), 220, y - 8);
        rotuloValor(cs, "DESCONTO", texto(xml, "vDesc"), 330, y - 8);
        rotuloValor(cs, "VALOR TOTAL NF-e", texto(xml, "vNF"), 445, y - 8);
        return y - 43;
    }

    private float cabecalhoItens(PDPageContentStream cs, float y) throws Exception {
        caixa(cs, M, y - 18, 547, 18);
        texto(cs, "CÓDIGO   DESCRIÇÃO DO PRODUTO / SERVIÇO                         NCM       CFOP  UN   QTD.       V.UNIT.       V.TOTAL", M + 3, y - 12, 5.5f, BOLD);
        return y - 21;
    }

    private float item(PDPageContentStream cs, Element det, float y) throws Exception {
        Element prod = filhoElemento(det, "prod");
        caixa(cs, M, y - 17, 547, 17);
        String linha = pad(filho(prod, "cProd"), 9) + " " + pad(limitar(filho(prod, "xProd"), 52), 52)
                + " " + pad(filho(prod, "NCM"), 8) + " " + pad(filho(prod, "CFOP"), 5)
                + " " + pad(filho(prod, "uCom"), 4) + " " + pad(filho(prod, "qCom"), 10)
                + " " + pad(filho(prod, "vUnCom"), 12) + " " + filho(prod, "vProd");
        texto(cs, linha, M + 3, y - 11, 5.3f, NORMAL);
        return y - 17;
    }

    private void rodape(PDPageContentStream cs, Document xml, float y) throws Exception {
        float topo = Math.min(y - 8, 90);
        caixa(cs, M, 28, 547, Math.max(42, topo - 28));
        texto(cs, "DADOS ADICIONAIS", M + 4, topo - 10, 6, BOLD);
        texto(cs, limitar(texto(xml, "infCpl"), 150), M + 4, topo - 23, 6, NORMAL);
    }

    private Document parseSeguro(byte[] xml) {
        if (xml.length > 2 * 1024 * 1024) throw new ApiException("XML autorizado excede o limite seguro.",
                HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/danfe");
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) { throw new ApiException("XML autorizado inválido.", HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/danfe"); }
    }

    private void codigoBarras(PDPageContentStream cs, String value, float x, float y, float w, float h) throws Exception {
        BitMatrix bits = new MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, (int) w, (int) h);
        float bw = w / bits.getWidth(), bh = h / bits.getHeight();
        for (int ix = 0; ix < bits.getWidth(); ix++) for (int iy = 0; iy < bits.getHeight(); iy++) if (bits.get(ix, iy))
            cs.addRect(x + ix * bw, y + (bits.getHeight() - iy - 1) * bh, bw + .1f, bh + .1f);
        cs.fill();
    }

    private void caixa(PDPageContentStream cs, float x, float y, float w, float h) throws IOException { cs.addRect(x, y, w, h); cs.stroke(); }
    private void texto(PDPageContentStream cs, String value, float x, float y, float size, PDType1Font font) throws IOException {
        String seguro = sanitizar(value); cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(seguro); cs.endText();
    }
    private void rotuloValor(PDPageContentStream cs, String rotulo, String valor, float x, float y) throws IOException {
        texto(cs, rotulo, x, y, 5, BOLD); texto(cs, limitar(valor, 48), x, y - 10, 7, NORMAL);
    }
    private String texto(Document d, String nome) { NodeList n = d.getElementsByTagNameNS("*", nome); return n.getLength() == 0 ? "" : n.item(n.getLength() - 1).getTextContent(); }
    private Element primeiro(Document d, String nome) { NodeList n = d.getElementsByTagNameNS("*", nome); return n.getLength() == 0 ? null : (Element) n.item(0); }
    private List<Element> elementos(Document d, String nome) { NodeList n=d.getElementsByTagNameNS("*",nome); List<Element> r=new ArrayList<>(); for(int i=0;i<n.getLength();i++)r.add((Element)n.item(i)); return r; }
    private Element filhoElemento(Element e, String nome) { if(e==null)return null; NodeList n=e.getElementsByTagNameNS("*",nome); return n.getLength()==0?null:(Element)n.item(0); }
    private String filho(Element e, String nome) { Element n=filhoElemento(e,nome); return n==null?"":n.getTextContent(); }
    private String endereco(Element e) { Element a=filhoElemento(e,"enderDest"); return limitar(filho(a,"xLgr")+", "+filho(a,"nro")+" - "+filho(a,"xMun")+"/"+filho(a,"UF"),65); }
    private String primeiroNaoVazio(String... vs) { for(String v:vs)if(v!=null&&!v.isBlank())return v;return ""; }
    private String limitar(String v,int n){return v==null?"":v.substring(0,Math.min(n,v.length()));}
    private String pad(String v,int n){String s=limitar(v,n);return s+" ".repeat(Math.max(0,n-s.length()));}
    private String agruparChave(String c){return c.replaceAll("(.{4})(?!$)","$1 ");}
    private String sanitizar(String v){return (v==null?"":v).replaceAll("[\\p{Cntrl}&&[^\\t]]"," ").replace('–','-').replace('—','-');}
}
