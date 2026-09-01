package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "eventos_fiscais", uniqueConstraints = @UniqueConstraint(
        name = "uk_evento_documento_tipo_sequencia", columnNames = {"documento_id", "tipo", "sequencia"}),
        indexes = @Index(name = "idx_evento_empresa_criado", columnList = "empresa_id, criado_em"))
@Getter @NoArgsConstructor
public class EventoFiscal {
    public enum Tipo { CCE }
    public enum Status { ACEITO, REJEITADO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "documento_id", nullable = false) private Long documentoId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Tipo tipo;
    @Column(nullable = false) private int sequencia;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 10) private String codigo;
    @Column(length = 255) private String motivo;
    @Column(length = 60) private String protocolo;
    @Lob @Column(name = "conteudo_cifrado", nullable = false, columnDefinition = "LONGBLOB") private byte[] conteudoCifrado;
    @Column(nullable = false, length = 12) private byte[] nonce;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;

    public EventoFiscal(Long empresaId, Long documentoId, int sequencia, Status status, String codigo,
                        String motivo, String protocolo, byte[] conteudoCifrado, byte[] nonce, String sha256) {
        this.empresaId = empresaId; this.documentoId = documentoId; this.tipo = Tipo.CCE;
        this.sequencia = sequencia; this.status = status; this.codigo = codigo; this.motivo = motivo;
        this.protocolo = protocolo; this.conteudoCifrado = conteudoCifrado.clone(); this.nonce = nonce.clone();
        this.sha256 = sha256; this.criadoEm = Instant.now();
    }
}
