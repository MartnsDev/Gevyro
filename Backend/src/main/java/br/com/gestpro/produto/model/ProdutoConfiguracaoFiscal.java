package br.com.gestpro.produto.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.*;

@Entity
@Table(name = "produto_configuracoes_fiscais", uniqueConstraints = @UniqueConstraint(
        name = "uk_produto_fiscal_versao", columnNames = {"produto_id", "versao"}), indexes = {
        @Index(name = "idx_produto_fiscal_vigencia", columnList = "produto_id, vigencia_inicio, vigencia_fim"),
        @Index(name = "idx_produto_fiscal_empresa", columnList = "empresa_id")
})
@Getter @NoArgsConstructor
public class ProdutoConfiguracaoFiscal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "produto_id", nullable = false) private Long produtoId;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(nullable = false) private int versao;
    @Column(name = "vigencia_inicio", nullable = false) private LocalDate vigenciaInicio;
    @Column(name = "vigencia_fim") private LocalDate vigenciaFim;
    @Column(nullable = false, length = 8) private String ncm;
    @Column(length = 7) private String cest;
    @Column(nullable = false, length = 1) private String origem;
    @Column(name = "unidade_comercial", nullable = false, length = 6) private String unidadeComercial;
    @Column(name = "unidade_tributavel", nullable = false, length = 6) private String unidadeTributavel;
    @Column(length = 14) private String gtin;
    @Column(name = "cfop_padrao", nullable = false, length = 4) private String cfopPadrao;
    @Column(length = 3) private String csosn;
    @Column(name = "cst_icms", length = 3) private String cstIcms;
    @Column(name = "cst_ipi", length = 2) private String cstIpi;
    @Column(name = "cst_pis", nullable = false, length = 2) private String cstPis;
    @Column(name = "cst_cofins", nullable = false, length = 2) private String cstCofins;
    @Column(name = "cst_ibs_cbs", length = 3) private String cstIbsCbs;
    @Column(name = "cclass_trib", length = 6) private String cClassTrib;
    @Column(name = "confirmado_responsavel", nullable = false) private boolean confirmadoResponsavel;
    @Column(name = "criado_por", nullable = false, length = 320) private String criadoPor;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;

    public ProdutoConfiguracaoFiscal(Long produtoId, Long empresaId, int versao, LocalDate inicio,
            String ncm, String cest, String origem, String unidadeComercial, String unidadeTributavel,
            String gtin, String cfopPadrao, String csosn, String cstIcms, String cstIpi, String cstPis,
            String cstCofins, String cstIbsCbs, String cClassTrib, String criadoPor) {
        this.produtoId=produtoId; this.empresaId=empresaId; this.versao=versao; this.vigenciaInicio=inicio;
        this.ncm=ncm; this.cest=cest; this.origem=origem; this.unidadeComercial=unidadeComercial;
        this.unidadeTributavel=unidadeTributavel; this.gtin=gtin; this.cfopPadrao=cfopPadrao;
        this.csosn=csosn; this.cstIcms=cstIcms; this.cstIpi=cstIpi; this.cstPis=cstPis;
        this.cstCofins=cstCofins; this.cstIbsCbs=cstIbsCbs; this.cClassTrib=cClassTrib;
        this.confirmadoResponsavel=true; this.criadoPor=criadoPor; this.criadoEm=Instant.now();
    }

    public void encerrar(LocalDate fim) { this.vigenciaFim = fim; }
}
