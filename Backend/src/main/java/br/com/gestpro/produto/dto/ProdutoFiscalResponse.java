package br.com.gestpro.produto.dto;

import br.com.gestpro.produto.model.ProdutoConfiguracaoFiscal;
import java.time.*;

public record ProdutoFiscalResponse(Long id, Long produtoId, int versao, LocalDate vigenciaInicio,
        LocalDate vigenciaFim, String ncm, String cest, String origem, String unidadeComercial,
        String unidadeTributavel, String gtin, String cfopPadrao, String csosn, String cstIcms,
        String cstIpi, String cstPis, String cstCofins, String cstIbsCbs, String cclassTrib,
        boolean confirmadoResponsavel, Instant criadoEm) {
    public static ProdutoFiscalResponse of(ProdutoConfiguracaoFiscal c) {
        return new ProdutoFiscalResponse(c.getId(),c.getProdutoId(),c.getVersao(),c.getVigenciaInicio(),c.getVigenciaFim(),
                c.getNcm(),c.getCest(),c.getOrigem(),c.getUnidadeComercial(),c.getUnidadeTributavel(),c.getGtin(),
                c.getCfopPadrao(),c.getCsosn(),c.getCstIcms(),c.getCstIpi(),c.getCstPis(),c.getCstCofins(),
                c.getCstIbsCbs(),c.getCClassTrib(),c.isConfirmadoResponsavel(),c.getCriadoEm());
    }
}
