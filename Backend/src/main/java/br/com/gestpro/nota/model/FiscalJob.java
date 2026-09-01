package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "fiscal_jobs", indexes = {
        @Index(name = "idx_fiscal_job_fila", columnList = "status, proxima_tentativa_em"),
        @Index(name = "idx_fiscal_job_documento", columnList = "documento_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_fiscal_job_idempotencia", columnNames = "idempotencia_id"))
@Getter @NoArgsConstructor
public class FiscalJob {
    public enum Tipo { EMISSAO, CONSULTA_SITUACAO }
    public enum Status { PENDENTE, PROCESSANDO, CONCLUIDO, FALHOU, RESULTADO_DESCONHECIDO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "documento_id", nullable = false) private Long documentoId;
    @Column(name = "idempotencia_id", nullable = false) private Long idempotenciaId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Tipo tipo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(nullable = false, length = 320) private String ator;
    @Column(name = "correlation_id", nullable = false, length = 100) private String correlationId;
    @Column(nullable = false) private int tentativas;
    @Column(name = "max_tentativas", nullable = false) private int maxTentativas;
    @Column(name = "proxima_tentativa_em", nullable = false) private Instant proximaTentativaEm;
    @Column(name = "iniciado_em") private Instant iniciadoEm;
    @Column(name = "finalizado_em") private Instant finalizadoEm;
    @Column(name = "ultimo_erro", length = 500) private String ultimoErro;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Version private long versao;

    public FiscalJob(Long empresaId, Long documentoId, Long idempotenciaId, String ator, String correlationId, int maxTentativas) {
        this.empresaId = empresaId; this.documentoId = documentoId; this.idempotenciaId = idempotenciaId;
        this.tipo = Tipo.EMISSAO; this.status = Status.PENDENTE; this.ator = ator; this.correlationId = correlationId;
        this.maxTentativas = maxTentativas; this.proximaTentativaEm = Instant.now(); this.criadoEm = Instant.now();
    }
    public void iniciar() { status = Status.PROCESSANDO; tentativas++; iniciadoEm = Instant.now(); }
    public void concluir() { status = Status.CONCLUIDO; finalizadoEm = Instant.now(); ultimoErro = null; }
    public void agendarConsulta(Instant quando, String erro) {
        tipo = Tipo.CONSULTA_SITUACAO; status = Status.PENDENTE; proximaTentativaEm = quando;
        ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 500));
    }
    public void falhar(String erro, boolean desconhecido) {
        status = desconhecido ? Status.RESULTADO_DESCONHECIDO : Status.FALHOU;
        finalizadoEm = Instant.now(); ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 500));
    }
}
