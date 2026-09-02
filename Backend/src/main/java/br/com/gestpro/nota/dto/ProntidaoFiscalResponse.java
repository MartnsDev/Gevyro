package br.com.gestpro.nota.dto;

import java.util.List;

public record ProntidaoFiscalResponse(
        Long empresaId,
        int percentual,
        List<Requisito> requisitos,
        boolean nfePronta,
        boolean nfcePronta,
        boolean nfsePronta,
        List<String> alertas
) {
    public record Requisito(String codigo, String descricao, boolean concluido) {}
}
