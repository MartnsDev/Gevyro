package br.com.gestpro.nota.service;

import br.com.gestpro.nota.config.NotaFiscalConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Gera os dados do QR Code NFC-e v3, sem CSC. Na contingência offline a
 * assinatura RSA-SHA1 é uma exigência do leiaute oficial (NT 2025.001), não
 * uma escolha criptográfica da aplicação.
 */
@Service
public class NfceQrCodeService {
    public DadosQrCode gerarOnline(String chaveAcesso, String uf, boolean homologacao) {
        if (chaveAcesso == null || !chaveAcesso.matches("[0-9]{44}"))
            throw new IllegalArgumentException("Chave inválida para QR Code NFC-e.");
        String url = NotaFiscalConfig.getNfceQrCodeUrl(uf, homologacao) + "?p=" + chaveAcesso
                + "|3|" + (homologacao ? "2" : "1");
        return new DadosQrCode(url, NotaFiscalConfig.getNfceConsultaUrl(uf, homologacao), "3.00", false);
    }

    public DadosQrCode gerarOffline(String chaveAcesso, String uf, boolean homologacao,
                                    LocalDateTime dataEmissao, BigDecimal valorTotal,
                                    String cpfCnpjDestinatario, byte[] pfx, String senha) {
        if (pfx == null || pfx.length == 0 || senha == null)
            throw new IllegalArgumentException("Certificado A1 obrigatório para QR Code offline.");
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new java.io.ByteArrayInputStream(pfx), senha.toCharArray());
            String alias = keyStore.aliases().nextElement();
            PrivateKey chavePrivada = (PrivateKey) keyStore.getKey(alias, senha.toCharArray());
            return gerarOffline(chaveAcesso, uf, homologacao, dataEmissao, valorTotal,
                    cpfCnpjDestinatario, chavePrivada);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar o QR Code offline com o certificado A1.", e);
        }
    }

    DadosQrCode gerarOffline(String chaveAcesso, String uf, boolean homologacao,
                             LocalDateTime dataEmissao, BigDecimal valorTotal,
                             String cpfCnpjDestinatario, PrivateKey chavePrivada) {
        if (chaveAcesso == null || !chaveAcesso.matches("[0-9]{44}"))
            throw new IllegalArgumentException("Chave inválida para QR Code NFC-e.");
        if (dataEmissao == null || valorTotal == null || valorTotal.signum() < 0 || chavePrivada == null)
            throw new IllegalArgumentException("Dados obrigatórios ausentes para QR Code offline.");

        String documento = cpfCnpjDestinatario == null ? "" : cpfCnpjDestinatario.replaceAll("[^0-9]", "");
        String tipoDocumento;
        if (documento.isEmpty()) tipoDocumento = "";
        else if (documento.length() == 11) tipoDocumento = "2";
        else if (documento.length() == 14) tipoDocumento = "1";
        else throw new IllegalArgumentException("CPF/CNPJ do destinatário inválido para QR Code offline.");

        String parametros = String.join("|", chaveAcesso, "3", homologacao ? "2" : "1",
                dataEmissao.format(DateTimeFormatter.ofPattern("dd")),
                valorTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                tipoDocumento, documento);
        try {
            if (!"RSA".equalsIgnoreCase(chavePrivada.getAlgorithm()))
                throw new IllegalArgumentException("O certificado da NFC-e deve possuir chave RSA.");
            Signature assinatura = Signature.getInstance("SHA1withRSA");
            assinatura.initSign(chavePrivada);
            assinatura.update(parametros.getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getEncoder().encodeToString(assinatura.sign());
            String url = NotaFiscalConfig.getNfceQrCodeUrl(uf, homologacao) + "?p=" + parametros + "|"
                    + URLEncoder.encode(base64, StandardCharsets.UTF_8);
            return new DadosQrCode(url, NotaFiscalConfig.getNfceConsultaUrl(uf, homologacao), "3.00", false);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Falha criptográfica ao assinar QR Code offline.", e);
        }
    }

    public record DadosQrCode(String qrCodeUrl, String consultaUrl, String versao, boolean usaCsc) {}
}
