package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.nota.model.NotaFiscal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class GerarChaveAcesso {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Geração da chave
    public String gerar(NotaFiscal nota, String cnpjEmitente, String ufCodigo) {

        String aamm    = nota.getDataEmissao().format(DateTimeFormatter.ofPattern("yyMM"));
        String cnpj    = cnpjEmitente.replaceAll("[^0-9]", "");
        String modelo  = nota.getTipo().getModelo();
        String serie   = String.format("%03d",
                Integer.parseInt(nota.getSerie() != null ? nota.getSerie() : "1"));
        String nNF     = String.format("%09d", nota.getNumeroNota());
        String tpEmis  = Boolean.TRUE.equals(nota.getEmContingencia()) ? "9" : "1";
        String cNF     = String.format("%08d", RANDOM.nextInt(99_999_999));

        String chaveSemDV = ufCodigo + aamm + cnpj + modelo + serie + nNF + tpEmis + cNF;
        String dv         = calcularDigitoVerificador(chaveSemDV);
        String chave      = chaveSemDV + dv;

        log.debug("Chave de acesso da NF-e gerada.");
        return chave;
    }

    // Dígito verificador
    private String calcularDigitoVerificador(String chave) {
        int soma          = 0;
        int multiplicador = 2;

        for (int i = chave.length() - 1; i >= 0; i--) {
            soma         += Character.getNumericValue(chave.charAt(i)) * multiplicador;
            multiplicador = multiplicador == 9 ? 2 : multiplicador + 1;
        }

        int resto = soma % 11;
        int dv    = (resto < 2) ? 0 : 11 - resto;
        return String.valueOf(dv);
    }

    // Tabela de UFs
    public static String getCodigoUf(String uf) {
        if (uf == null) return "35";
        return switch (uf.toUpperCase().trim()) {
            case "RO" -> "11"; case "AC" -> "12"; case "AM" -> "13"; case "RR" -> "14";
            case "PA" -> "15"; case "AP" -> "16"; case "TO" -> "17"; case "MA" -> "21";
            case "PI" -> "22"; case "CE" -> "23"; case "RN" -> "24"; case "PB" -> "25";
            case "PE" -> "26"; case "AL" -> "27"; case "SE" -> "28"; case "BA" -> "29";
            case "MG" -> "31"; case "ES" -> "32"; case "RJ" -> "33"; case "SP" -> "35";
            case "PR" -> "41"; case "SC" -> "42"; case "RS" -> "43"; case "MS" -> "50";
            case "MT" -> "51"; case "GO" -> "52"; case "DF" -> "53";
            default   -> "35";
        };
    }
}
