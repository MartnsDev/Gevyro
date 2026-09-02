package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.nota.RegimeTributario;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.dto.EmpresaInfo;
import br.com.gestpro.nota.model.ItemNotaFiscal;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.service.NfceQrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera o XML da NF-e / NFC-e conforme leiaute 4.0 da SEFAZ.
 * Referência: NT 2019.001 v1.20 e NT 2021.004 (NFC-e).
 *
 * CORREÇÕES aplicadas nesta versão:
 *  - {@code LocalDateTime.atZone()} substituído por {@code LocalDateTime.atZone(ZoneId)}
 *    (LocalDateTime não possui atZoneSameInstant — esse método pertence a ZonedDateTime).
 *  - Separação clara entre regime Simples Nacional (CSOSN) e Lucro Presumido/Real (CST).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmlGeneratorService {

    private final NfceQrCodeService nfceQrCodeService;

    private static final ZoneId      ZONE_BR = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    // Ponto de entrada

    /**
     * Gera o XML completo da NF-e ou NFC-e a partir dos dados da nota e da empresa emitente.
     *
     * @param nota         Entidade persistida da nota fiscal
     * @param empresa      Dados completos do emitente (buscados do serviço de empresa)
     * @param itens        Lista de itens da nota
     * @param chaveAcesso  Chave de 44 dígitos já calculada pelo {@link GerarChaveAcesso}
     * @return String XML sem assinatura digital (a assinatura é aplicada depois pelo {@link AssinaturaDigitalService})
     */
    public String gerarXmlNfe(NotaFiscal nota,
                              EmpresaInfo empresa,
                              List<ItemNotaFiscal> itens,
                              String chaveAcesso,
                              boolean homologacao) {

        boolean isNfce          = TipoNota.NFCE.equals(nota.getTipo());
        boolean isSimplesNacional = RegimeTributario.SIMPLES_NACIONAL.equals(empresa.getRegimeTributario())
                || RegimeTributario.SIMPLES_NACIONAL_EXCESSO.equals(empresa.getRegimeTributario());

        // Formata a data/hora no fuso brasileiro com offset (obrigatório pela SEFAZ)
        String dhEmi = nota.getDataEmissao()
                .atZone(ZONE_BR)           // ← CORRIGIDO: LocalDateTime → ZonedDateTime
                .format(DT_FORMAT);

        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">");
        xml.append("<infNFe versao=\"4.00\" Id=\"NFe").append(chaveAcesso).append("\">");

        // Seções do XML em métodos dedicados para legibilidade
        appendIde(xml, nota, empresa, chaveAcesso, isNfce, dhEmi, homologacao);
        appendEmitente(xml, empresa);
        appendDestinatario(xml, nota, isNfce);
        appendItens(xml, itens, isSimplesNacional);
        appendTotal(xml, nota);
        appendTransporte(xml);
        appendPagamento(xml, nota);
        appendInfoAdicionais(xml, nota);

        xml.append("</infNFe>");
        if (isNfce) appendNfceSuplementar(xml, nota, empresa, chaveAcesso, homologacao);
        xml.append("</NFe>");

        log.debug("XML da NF-e gerado com {} bytes.", xml.length());
        return xml.toString();
    }

    // Seções do XML

    /** Bloco <ide> — identificação do documento fiscal. */
    private void appendIde(StringBuilder xml, NotaFiscal nota, EmpresaInfo empresa,
                           String chaveAcesso, boolean isNfce, String dhEmi, boolean homologacao) {
        xml.append("<ide>");
        xml.append("<cUF>").append(GerarChaveAcesso.getCodigoUf(empresa.getUf())).append("</cUF>");
        xml.append("<cNF>").append(chaveAcesso, 35, 43).append("</cNF>");
        xml.append("<natOp>").append(esc(nota.getNaturezaOperacao())).append("</natOp>");
        xml.append("<mod>").append(nota.getTipo().getModelo()).append("</mod>");
        xml.append("<serie>").append(nota.getSerie() != null ? nota.getSerie() : "1").append("</serie>");
        xml.append("<nNF>").append(nota.getNumeroNota()).append("</nNF>");
        xml.append("<dhEmi>").append(dhEmi).append("</dhEmi>");
        if (!isNfce) {
            // NF-e exige data/hora de saída; NFC-e não possui esse campo.
            xml.append("<dhSaiEnt>").append(dhEmi).append("</dhSaiEnt>");
        }
        xml.append("<tpNF>1</tpNF>");              // 1 = Saída
        xml.append("<idDest>1</idDest>");           // 1 = Operação interna
        xml.append("<cMunFG>").append(empresa.getCodigoIbge()).append("</cMunFG>");
        xml.append("<tpImp>").append(isNfce ? "4" : "1").append("</tpImp>"); // 4=NFC-e; 1=DANFE retrato
        xml.append("<tpEmis>").append(Boolean.TRUE.equals(nota.getEmContingencia()) ? "9" : "1").append("</tpEmis>");
        xml.append("<cDV>").append(chaveAcesso.charAt(43)).append("</cDV>");
        xml.append("<tpAmb>").append(homologacao ? "2" : "1").append("</tpAmb>");
        xml.append("<finNFe>1</finNFe>");           // 1 = Normal
        xml.append("<indFinal>").append(isNfce ? "1" : "0").append("</indFinal>");
        xml.append("<indPres>").append(isNfce ? "1" : "9").append("</indPres>");
        xml.append("<procEmi>0</procEmi>");
        xml.append("<verProc>GestPro-1.0</verProc>");
        xml.append("</ide>");
    }

    /** Bloco <emit> — dados do emitente. */
    private void appendEmitente(StringBuilder xml, EmpresaInfo empresa) {
        xml.append("<emit>");
        xml.append("<CNPJ>").append(digitos(empresa.getCnpj())).append("</CNPJ>");
        xml.append("<xNome>").append(esc(empresa.getRazaoSocial())).append("</xNome>");
        if (empresa.getNomeFantasia() != null && !empresa.getNomeFantasia().isBlank()) {
            xml.append("<xFant>").append(esc(empresa.getNomeFantasia())).append("</xFant>");
        }
        xml.append("<enderEmit>");
        xml.append("<xLgr>").append(esc(empresa.getLogradouro())).append("</xLgr>");
        xml.append("<nro>").append(esc(empresa.getNumero())).append("</nro>");
        if (empresa.getComplemento() != null && !empresa.getComplemento().isBlank()) {
            xml.append("<xCpl>").append(esc(empresa.getComplemento())).append("</xCpl>");
        }
        xml.append("<xBairro>").append(esc(empresa.getBairro())).append("</xBairro>");
        xml.append("<cMun>").append(empresa.getCodigoIbge()).append("</cMun>");
        xml.append("<xMun>").append(esc(empresa.getMunicipio())).append("</xMun>");
        xml.append("<UF>").append(empresa.getUf()).append("</UF>");
        xml.append("<CEP>").append(digitos(empresa.getCep())).append("</CEP>");
        xml.append("<cPais>1058</cPais><xPais>Brasil</xPais>");
        if (empresa.getTelefone() != null && !empresa.getTelefone().isBlank()) {
            xml.append("<fone>").append(digitos(empresa.getTelefone())).append("</fone>");
        }
        xml.append("</enderEmit>");
        xml.append("<IE>").append(empresa.getInscricaoEstadual() != null
                ? digitos(empresa.getInscricaoEstadual()) : "ISENTO").append("</IE>");
        if (empresa.getInscricaoMunicipal() != null) {
            xml.append("<IM>").append(empresa.getInscricaoMunicipal()).append("</IM>");
        }
        xml.append("<CRT>").append(empresa.getRegimeTributario().getCodigo()).append("</CRT>");
        xml.append("</emit>");
    }

    /** Bloco <dest> — dados do destinatário (omitido para NFC-e sem identificação). */
    private void appendDestinatario(StringBuilder xml, NotaFiscal nota, boolean isNfce) {
        // NFC-e pode ser emitida sem destinatário identificado (consumidor final anônimo)
        if (nota.getClienteNome() == null || nota.getClienteNome().isBlank()) {
            return;
        }
        xml.append("<dest>");
        String cpfCnpj = nota.getClienteCpfCnpj() != null ? digitos(nota.getClienteCpfCnpj()) : "";
        if (cpfCnpj.length() == 14) {
            xml.append("<CNPJ>").append(cpfCnpj).append("</CNPJ>");
        } else if (cpfCnpj.length() == 11) {
            xml.append("<CPF>").append(cpfCnpj).append("</CPF>");
        }
        xml.append("<xNome>").append(esc(nota.getClienteNome())).append("</xNome>");
        xml.append("<indIEDest>9</indIEDest>"); // 9 = não contribuinte
        xml.append("</dest>");
    }

    /** Blocos <det> — itens da nota. */
    private void appendItens(StringBuilder xml, List<ItemNotaFiscal> itens, boolean isSimplesNacional) {
        int numItem = 1;
        for (ItemNotaFiscal item : itens) {
            xml.append("<det nItem=\"").append(numItem++).append("\">");
            appendProd(xml, item);
            appendImposto(xml, item, isSimplesNacional);
            xml.append("</det>");
        }
    }

    /** Sub-bloco <prod> dentro de <det>. */
    private void appendProd(StringBuilder xml, ItemNotaFiscal item) {
        String codProd = item.getCodigoProduto() != null && !item.getCodigoProduto().isBlank()
                ? item.getCodigoProduto()
                : String.valueOf(item.getProdutoId());
        String unidade = item.getUnidade() != null ? item.getUnidade() : "UN";

        xml.append("<prod>");
        xml.append("<cProd>").append(esc(codProd)).append("</cProd>");
        xml.append("<cEAN>SEM GTIN</cEAN>");
        xml.append("<xProd>").append(esc(item.getDescricao())).append("</xProd>");
        xml.append("<NCM>").append(item.getNcm()).append("</NCM>");
        xml.append("<CFOP>").append(item.getCfop()).append("</CFOP>");
        xml.append("<uCom>").append(unidade).append("</uCom>");
        xml.append("<qCom>").append(fmt(item.getQuantidade(), 4)).append("</qCom>");
        xml.append("<vUnCom>").append(fmt(item.getValorUnitario(), 4)).append("</vUnCom>");
        xml.append("<vProd>").append(fmt(item.getValorBruto(), 2)).append("</vProd>");
        xml.append("<cEANTrib>SEM GTIN</cEANTrib>");
        xml.append("<uTrib>").append(unidade).append("</uTrib>");
        xml.append("<qTrib>").append(fmt(item.getQuantidade(), 4)).append("</qTrib>");
        xml.append("<vUnTrib>").append(fmt(item.getValorUnitario(), 4)).append("</vUnTrib>");
        if (item.getValorDesconto() != null && item.getValorDesconto().compareTo(BigDecimal.ZERO) > 0) {
            xml.append("<vDesc>").append(fmt(item.getValorDesconto(), 2)).append("</vDesc>");
        }
        xml.append("<indTot>1</indTot>");
        xml.append("</prod>");
    }

    /** Sub-bloco <imposto> com ICMS, PIS e COFINS. */
    private void appendImposto(StringBuilder xml, ItemNotaFiscal item, boolean isSimplesNacional) {
        xml.append("<imposto>");

        xml.append("<ICMS>");
        if (isSimplesNacional) {
            String csosn = item.getCsosn() != null ? item.getCsosn() : "400";
            xml.append("<ICMSSN").append(csosn).append(">");
            xml.append("<orig>0</orig>");
            xml.append("<CSOSN>").append(csosn).append("</CSOSN>");
            // CSOSN 500 exige campos de ST retido
            if ("500".equals(csosn)) {
                xml.append("<vBCSTRet>0.00</vBCSTRet>");
                xml.append("<pST>0.00</pST>");
                xml.append("<vICMSSTRet>0.00</vICMSSTRet>");
            }
            xml.append("</ICMSSN").append(csosn).append(">");
        } else {
            String cst = item.getCstIcms() != null ? item.getCstIcms() : "00";
            xml.append("<ICMS").append(cst).append(">");
            xml.append("<orig>0</orig>");
            xml.append("<CST>").append(cst).append("</CST>");
            if ("00".equals(cst) || "10".equals(cst) || "20".equals(cst)) {
                BigDecimal bcIcms = item.getIcmsBaseCalculo() != null && item.getIcmsBaseCalculo().compareTo(BigDecimal.ZERO) > 0
                        ? item.getIcmsBaseCalculo() : item.getValorTotal();
                BigDecimal aliq = item.getIcmsAliquota() != null ? item.getIcmsAliquota() : BigDecimal.ZERO;
                BigDecimal vIcms = bcIcms.multiply(aliq).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                xml.append("<modBC>3</modBC>");
                xml.append("<vBC>").append(fmt(bcIcms, 2)).append("</vBC>");
                xml.append("<pICMS>").append(fmt(aliq, 2)).append("</pICMS>");
                xml.append("<vICMS>").append(fmt(vIcms, 2)).append("</vICMS>");
            }
            xml.append("</ICMS").append(cst).append(">");
        }
        xml.append("</ICMS>");

        xml.append("<PIS>");
        String cstPis = item.getCstPis() != null ? item.getCstPis() : (isSimplesNacional ? "07" : "01");
        if ("07".equals(cstPis) || "08".equals(cstPis) || "09".equals(cstPis)) {
            xml.append("<PISNT><CST>").append(cstPis).append("</CST></PISNT>");
        } else {
            BigDecimal bcPis = item.getPisBaseCalculo() != null && item.getPisBaseCalculo().compareTo(BigDecimal.ZERO) > 0
                    ? item.getPisBaseCalculo() : item.getValorTotal();
            BigDecimal aliqPis = item.getPisAliquota() != null ? item.getPisAliquota() : new BigDecimal("0.65");
            BigDecimal vPis = bcPis.multiply(aliqPis).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            xml.append("<PISAliq>");
            xml.append("<CST>").append(cstPis).append("</CST>");
            xml.append("<vBC>").append(fmt(bcPis, 2)).append("</vBC>");
            xml.append("<pPIS>").append(fmt(aliqPis, 4)).append("</pPIS>");
            xml.append("<vPIS>").append(fmt(vPis, 2)).append("</vPIS>");
            xml.append("</PISAliq>");
        }
        xml.append("</PIS>");

        xml.append("<COFINS>");
        String cstCofins = item.getCstCofins() != null ? item.getCstCofins() : (isSimplesNacional ? "07" : "01");
        if ("07".equals(cstCofins) || "08".equals(cstCofins) || "09".equals(cstCofins)) {
            xml.append("<COFINSNT><CST>").append(cstCofins).append("</CST></COFINSNT>");
        } else {
            BigDecimal bcCofins = item.getCofinsBaseCalculo() != null && item.getCofinsBaseCalculo().compareTo(BigDecimal.ZERO) > 0
                    ? item.getCofinsBaseCalculo() : item.getValorTotal();
            BigDecimal aliqCofins = item.getCofinsAliquota() != null ? item.getCofinsAliquota() : new BigDecimal("3.00");
            BigDecimal vCofins = bcCofins.multiply(aliqCofins).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            xml.append("<COFINSAliq>");
            xml.append("<CST>").append(cstCofins).append("</CST>");
            xml.append("<vBC>").append(fmt(bcCofins, 2)).append("</vBC>");
            xml.append("<pCOFINS>").append(fmt(aliqCofins, 4)).append("</pCOFINS>");
            xml.append("<vCOFINS>").append(fmt(vCofins, 2)).append("</vCOFINS>");
            xml.append("</COFINSAliq>");
        }
        xml.append("</COFINS>");

        xml.append("</imposto>");
    }

    /** Bloco <total> — totais da nota. */
    private void appendTotal(StringBuilder xml, NotaFiscal nota) {
        xml.append("<total><ICMSTot>");
        xml.append("<vBC>0.00</vBC>");
        xml.append("<vICMS>").append(fmt(nota.getValorIcms(), 2)).append("</vICMS>");
        xml.append("<vICMSDeson>0.00</vICMSDeson>");
        xml.append("<vFCP>0.00</vFCP>");
        xml.append("<vBCST>0.00</vBCST>");
        xml.append("<vST>0.00</vST>");
        xml.append("<vFCPST>0.00</vFCPST>");
        xml.append("<vFCPSTRet>0.00</vFCPSTRet>");
        xml.append("<vProd>").append(fmt(nota.getValorProdutos(), 2)).append("</vProd>");
        xml.append("<vFrete>").append(fmt(nota.getValorFrete(), 2)).append("</vFrete>");
        xml.append("<vSeg>0.00</vSeg>");
        xml.append("<vDesc>").append(fmt(nota.getValorDesconto(), 2)).append("</vDesc>");
        xml.append("<vII>0.00</vII>");
        xml.append("<vIPI>0.00</vIPI>");
        xml.append("<vIPIDevol>0.00</vIPIDevol>");
        xml.append("<vPIS>").append(fmt(nota.getValorPis(), 2)).append("</vPIS>");
        xml.append("<vCOFINS>").append(fmt(nota.getValorCofins(), 2)).append("</vCOFINS>");
        xml.append("<vOutro>0.00</vOutro>");
        xml.append("<vNF>").append(fmt(nota.getValorTotal(), 2)).append("</vNF>");
        xml.append("</ICMSTot></total>");
    }

    /** Bloco <transp> — transporte (sem frete por padrão). */
    private void appendTransporte(StringBuilder xml) {
        xml.append("<transp><modFrete>9</modFrete></transp>"); // 9 = sem ocorrência de transporte
    }

    /** Bloco <pag> — forma de pagamento. */
    private void appendPagamento(StringBuilder xml, NotaFiscal nota) {
        BigDecimal total = nota.getValorTotal() != null ? nota.getValorTotal() : BigDecimal.ZERO;
        BigDecimal primeiro = nota.getValorPagamento1() != null ? nota.getValorPagamento1() : total;
        BigDecimal segundo = nota.getValorPagamento2();
        if (primeiro == null || primeiro.signum() < 0 || (segundo != null && segundo.signum() <= 0)
                || (nota.getFormaPagamento2() == null) != (segundo == null)
                || primeiro.add(segundo == null ? BigDecimal.ZERO : segundo).compareTo(total) != 0)
            throw new IllegalArgumentException("Pagamentos fiscais não correspondem ao total da nota.");
        xml.append("<pag>");
        xml.append("<detPag>");
        xml.append("<tPag>").append(nota.getFormaPagamento() != null
                ? nota.getFormaPagamento().getCodigo() : "01").append("</tPag>");
        xml.append("<vPag>").append(fmt(primeiro, 2)).append("</vPag>");
        xml.append("</detPag>");
        if (nota.getFormaPagamento2() != null) {
            xml.append("<detPag><tPag>").append(nota.getFormaPagamento2().getCodigo()).append("</tPag>")
                    .append("<vPag>").append(fmt(segundo, 2)).append("</vPag></detPag>");
        }
        xml.append("</pag>");
    }

    private void appendNfceSuplementar(StringBuilder xml, NotaFiscal nota, EmpresaInfo empresa,
                                       String chaveAcesso, boolean homologacao) {
        if (Boolean.TRUE.equals(nota.getEmContingencia()))
            throw new IllegalStateException("NFC-e em contingência offline ainda não possui QR Code v3 assinado e não pode ser gerada.");
        NfceQrCodeService.DadosQrCode dados = nfceQrCodeService.gerarOnline(chaveAcesso, empresa.getUf(), homologacao);
        xml.append("<infNFeSupl><qrCode><![CDATA[").append(dados.qrCodeUrl())
                .append("]]></qrCode><urlChave>").append(esc(dados.consultaUrl()))
                .append("</urlChave></infNFeSupl>");
    }

    /** Bloco <infAdic> — informações adicionais (opcional). */
    private void appendInfoAdicionais(StringBuilder xml, NotaFiscal nota) {
        if (nota.getInformacoesAdicionais() != null && !nota.getInformacoesAdicionais().isBlank()) {
            xml.append("<infAdic><infCpl>")
                    .append(esc(nota.getInformacoesAdicionais()))
                    .append("</infCpl></infAdic>");
        }
    }

    // Utilitários

    /** Formata BigDecimal com {@code scale} casas decimais. Trata nulos como zero. */
    private String fmt(BigDecimal val, int scale) {
        if (val == null) return "0." + "0".repeat(scale);
        return val.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /** Escapa caracteres especiais XML. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** Remove tudo que não for dígito numérico (para CNPJ, CPF, CEP, telefone). */
    private String digitos(String s) {
        return s != null ? s.replaceAll("[^0-9]", "") : "";
    }
}
