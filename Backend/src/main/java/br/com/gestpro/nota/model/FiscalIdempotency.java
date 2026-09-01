package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "fiscal_idempotency", uniqueConstraints = @UniqueConstraint(
        name = "uk_fiscal_idempotency_empresa_operacao_chave",
        columnNames = {"empresa_id", "operacao", "idempotency_key"}))
@Getter @NoArgsConstructor
public class FiscalIdempotency {
    public enum Status { EM_PROCESSAMENTO, CONCLUIDA, FALHOU, RESULTADO_DESCONHECIDO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "documento_id", nullable = false) private Long documentoId;
    @Column(nullable = false, length = 40) private String operacao;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;

    public FiscalIdempotency(Long empresaId, Long documentoId, String operacao, String key) {
        this.empresaId = empresaId; this.documentoId = documentoId; this.operacao = operacao;
        this.idempotencyKey = key; this.status = Status.EM_PROCESSAMENTO;
        this.criadoEm = Instant.now(); this.atualizadoEm = criadoEm;
    }
    public void concluir() { status = Status.CONCLUIDA; atualizadoEm = Instant.now(); }
    public void falhar() { status = Status.FALHOU; atualizadoEm = Instant.now(); }
    public void resultadoDesconhecido() { status = Status.RESULTADO_DESCONHECIDO; atualizadoEm = Instant.now(); }
}
