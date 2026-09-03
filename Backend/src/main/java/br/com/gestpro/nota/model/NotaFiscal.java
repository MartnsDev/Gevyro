package br.com.gestpro.nota.model;

import br.com.gestpro.nota.FormaPagamento;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.DocumentoFiscalAmbiente;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "notas_fiscais", indexes = {
        @Index(name = "idx_empresa_tipo_numero", columnList = "empresa_id, tipo, numero_nota"),
        @Index(name = "idx_chave_acesso",        columnList = "chave_acesso"),
        @Index(name = "idx_status",              columnList = "status")
}, uniqueConstraints = @UniqueConstraint(name="uk_nota_empresa_tipo_serie_numero",columnNames={"empresa_id","tipo","serie","numero_nota"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "venda_id", unique = true)
    private Long vendaId;

    @Column(name = "caixa_id")
    private Long caixaId;

    @Column(name = "cliente_nome", length = 200)
    private String clienteNome;

    @Column(name = "cliente_cpf_cnpj", length = 18)
    private String clienteCpfCnpj;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoNota tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotaFiscalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    @Builder.Default
    private DocumentoFiscalAmbiente ambiente = DocumentoFiscalAmbiente.LEGADO_DESCONHECIDO;


    @Column(name = "numero_nota")
    private Long numeroNota;

    @Column(name = "serie", length = 3)
    private String serie;

    @Column(name = "chave_acesso", length = 50, unique = true)
    private String chaveAcesso;

    @Column(name = "nfse_competencia") private LocalDate nfseCompetencia;
    @Column(name = "nfse_codigo_municipio_prestacao", length = 7) private String nfseCodigoMunicipioPrestacao;
    @Column(name = "nfse_codigo_tributacao_nacional", length = 6) private String nfseCodigoTributacaoNacional;
    @Column(name = "nfse_opcao_simples") private Integer nfseOpcaoSimples;
    @Column(name = "nfse_regime_especial") private Integer nfseRegimeEspecial;
    @Column(name = "nfse_tributacao_issqn") private Integer nfseTributacaoIssqn;
    @Column(name = "nfse_retencao_issqn") private Integer nfseRetencaoIssqn;
    @Column(name = "nfse_aliquota_issqn", precision = 5, scale = 2) private BigDecimal nfseAliquotaIssqn;


    @Column(name = "natureza_operacao", length = 100, nullable = false)
    private String naturezaOperacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 30)
    private FormaPagamento formaPagamento;

    @Column(name = "valor_pagamento1", precision = 15, scale = 2)
    private BigDecimal valorPagamento1;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento2", length = 30)
    private FormaPagamento formaPagamento2;

    @Column(name = "valor_pagamento2", precision = 15, scale = 2)
    private BigDecimal valorPagamento2;


    @Column(name = "valor_produtos", precision = 15, scale = 2)
    private BigDecimal valorProdutos;

    @Builder.Default
    @Column(name = "valor_frete", precision = 15, scale = 2)
    private BigDecimal valorFrete = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "valor_desconto", precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "valor_icms", precision = 15, scale = 2)
    private BigDecimal valorIcms = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "valor_pis", precision = 15, scale = 2)
    private BigDecimal valorPis = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "valor_cofins", precision = 15, scale = 2)
    private BigDecimal valorCofins = BigDecimal.ZERO;

    @Column(name = "valor_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorTotal;


    @Column(name = "data_emissao", nullable = false)
    private LocalDateTime dataEmissao;

    @Column(name = "data_autorizacao")
    private LocalDateTime dataAutorizacao;

    @Column(name = "protocolo", length = 60)
    private String protocolo;

    @Column(name = "motivo_rejeicao", length = 1000)
    private String motivoRejeicao;


    @Column(name = "xml_enviado", columnDefinition = "TEXT")
    private String xmlEnviado;

    @Column(name = "xml_retorno", columnDefinition = "TEXT")
    private String xmlRetorno;

    /** XML assinado + protocolo de autorização embutido (o documento fiscal válido). */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String xmlAutorizado;

    @Column(name = "danfe_pdf_path", length = 500)
    private String danfePdfPath;


    @Builder.Default
    @Column(name = "em_contingencia")
    private Boolean emContingencia = false;

    @Column(name = "data_inicio_contingencia")
    private LocalDateTime dataInicioContingencia;

    @Column(name = "justificativa_contingencia", length = 500)
    private String justificativaContingencia;


    @Column(name = "informacoes_adicionais", length = 500)
    private String informacoesAdicionais;

    @Builder.Default
    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemNotaFiscal> itens = new ArrayList<>();


    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // JPA Lifecycle

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null)    this.status    = NotaFiscalStatus.DIGITACAO;
        if (this.dataEmissao == null) this.dataEmissao = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Helpers bidirecionais

    /**
     * Adiciona um item e garante que o vínculo bidirecional esteja correto.
     * Use sempre este método (e não {@code getItens().add()}) para adicionar itens.
     */
    public void addItem(ItemNotaFiscal item) {
        this.itens.add(item);
        item.vincularNota(this);
    }

    /**
     * Remove um item garantindo consistência bidirecional.
     */
    public void removeItem(ItemNotaFiscal item) {
        this.itens.remove(item);
        item.vincularNota(null);
    }
}
