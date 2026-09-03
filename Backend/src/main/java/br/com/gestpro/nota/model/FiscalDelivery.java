package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "fiscal_deliveries", indexes = {
        @Index(name = "idx_fiscal_delivery_queue", columnList = "status, proxima_tentativa_em"),
        @Index(name = "idx_fiscal_delivery_empresa", columnList = "empresa_id, criado_em")
}, uniqueConstraints = @UniqueConstraint(name = "uk_fiscal_delivery_dedup", columnNames = "dedup_key"))
@Getter @NoArgsConstructor
public class FiscalDelivery {
    public enum Canal { EMAIL }
    public enum Status { AGUARDANDO_CONFIGURACAO, PENDENTE, PROCESSANDO, ENVIADO, FALHOU }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "documento_id", nullable = false) private Long documentoId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Canal canal;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Lob @Column(name = "destinatario_cifrado", nullable = false, columnDefinition = "BLOB") private byte[] destinatarioCifrado;
    @Column(name = "destinatario_nonce", nullable = false, length = 12) private byte[] destinatarioNonce;
    @Column(name = "dedup_key", nullable = false, length = 64) private String dedupKey;
    @Column(nullable = false) private int tentativas;
    @Column(name = "max_tentativas", nullable = false) private int maxTentativas;
    @Column(name = "proxima_tentativa_em") private Instant proximaTentativaEm;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;
    @Version private long versao;

    public FiscalDelivery(Long empresaId, Long documentoId, byte[] destinatarioCifrado,
                          byte[] destinatarioNonce, String dedupKey, boolean envioHabilitado) {
        this.empresaId = empresaId; this.documentoId = documentoId; this.canal = Canal.EMAIL;
        this.status = envioHabilitado ? Status.PENDENTE : Status.AGUARDANDO_CONFIGURACAO;
        this.destinatarioCifrado = destinatarioCifrado.clone(); this.destinatarioNonce = destinatarioNonce.clone();
        this.dedupKey = dedupKey; this.maxTentativas = 5;
        this.proximaTentativaEm = envioHabilitado ? Instant.now() : null;
        this.criadoEm = Instant.now(); this.atualizadoEm = this.criadoEm;
    }
}
