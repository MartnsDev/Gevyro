package br.com.gestpro.nota.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Impede que detalhes técnicos da auditoria se tornem um repositório paralelo de dados sensíveis. */
@Component
public class FiscalSensitiveDataRedactor {
    private static final Pattern EMAIL = Pattern.compile("(?i)(?<![\\w.+-])[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}(?![\\w.-])");
    private static final Pattern DOCUMENTO = Pattern.compile("(?<!\\d)(?:\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}|\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}|\\d{11}|\\d{14})(?!\\d)");
    private static final Pattern SEGREDO = Pattern.compile(
            "(?i)(\\b(?:senha|password|token|api[-_ ]?key|apikey|csc|certificado)[-_ ]?(?:id|valor|value|secret)?\\b\\s*[:=]\\s*)([^,;\\s]+)");

    public String sanitizar(String valor) {
        if (valor == null) return null;
        String semQuebras = valor.replaceAll("[\\r\\n\\t]", " ");
        String semSegredos = SEGREDO.matcher(semQuebras).replaceAll("$1[SEGREDO_REDACTED]");
        String semDocumentos = DOCUMENTO.matcher(semSegredos).replaceAll("[DOCUMENTO_REDACTED]");
        String seguro = EMAIL.matcher(semDocumentos).replaceAll("[EMAIL_REDACTED]");
        return seguro.substring(0, Math.min(1000, seguro.length()));
    }
}
