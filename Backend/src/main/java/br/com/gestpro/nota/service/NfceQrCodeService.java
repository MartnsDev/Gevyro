package br.com.gestpro.nota.service;

import br.com.gestpro.nota.config.NotaFiscalConfig;
import org.springframework.stereotype.Service;

/**
 * Gera os dados do QR Code NFC-e v3 sem alterar o XML transmitido.
 * A integração ao infNFeSupl permanecerá desativada até validação completa em homologação.
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

    public record DadosQrCode(String qrCodeUrl, String consultaUrl, String versao, boolean usaCsc) {}
}
