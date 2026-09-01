package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

@Entity @Immutable
@Table(name = "fiscal_audit_log", indexes = {
        @Index(name = "idx_fiscal_audit_empresa_data", columnList = "empresa_id, criado_em"),
        @Index(name = "idx_fiscal_audit_documento", columnList = "documento_id")
})
@Getter @NoArgsConstructor
public class FiscalAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "documento_id") private Long documentoId;
    @Column(nullable = false, length = 80) private String acao;
    @Column(nullable = false, length = 320) private String ator;
    @Column(nullable = false, length = 20) private String ambiente;
    @Column(nullable = false, length = 40) private String resultado;
    @Column(length = 1000) private String detalhes;
    @Column(name = "correlation_id", nullable = false, length = 100) private String correlationId;
    @Column(name = "hash_anterior", nullable = false, length = 64) private String hashAnterior;
    @Column(name = "hash_registro", nullable = false, unique = true, length = 64) private String hashRegistro;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;

    public FiscalAuditLog(Long empresaId, Long documentoId, String acao, String ator, String ambiente,
                          String resultado, String detalhes, String correlationId, String hashAnterior,
                          String hashRegistro, Instant criadoEm) {
        this.empresaId = empresaId; this.documentoId = documentoId; this.acao = acao; this.ator = ator;
        this.ambiente = ambiente; this.resultado = resultado; this.detalhes = detalhes;
        this.correlationId = correlationId; this.hashAnterior = hashAnterior;
        this.hashRegistro = hashRegistro; this.criadoEm = criadoEm;
    }
}
