package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "certificados_digitais", uniqueConstraints =
        @UniqueConstraint(name = "uk_certificado_empresa", columnNames = "empresa_id"))
@Getter @NoArgsConstructor
public class CertificadoDigital {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Lob @Column(name = "arquivo_cifrado", nullable = false, columnDefinition = "LONGBLOB") private byte[] arquivoCifrado;
    @Column(name = "arquivo_nonce", nullable = false, length = 12) private byte[] arquivoNonce;
    @Lob @Column(name = "senha_cifrada", nullable = false, columnDefinition = "BLOB") private byte[] senhaCifrada;
    @Column(name = "senha_nonce", nullable = false, length = 12) private byte[] senhaNonce;
    @Column(nullable = false, length = 500) private String titular;
    @Column(nullable = false, length = 500) private String emissor;
    @Column(name = "numero_serie", nullable = false, length = 100) private String numeroSerie;
    @Column(name = "valido_de", nullable = false) private Instant validoDe;
    @Column(name = "valido_ate", nullable = false) private Instant validoAte;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;

    public CertificadoDigital(Long empresaId) { this.empresaId = empresaId; }

    public void substituir(byte[] arquivoCifrado, byte[] arquivoNonce, byte[] senhaCifrada, byte[] senhaNonce,
                           String titular, String emissor, String numeroSerie, Instant validoDe, Instant validoAte) {
        this.arquivoCifrado = arquivoCifrado.clone(); this.arquivoNonce = arquivoNonce.clone();
        this.senhaCifrada = senhaCifrada.clone(); this.senhaNonce = senhaNonce.clone();
        this.titular = titular; this.emissor = emissor; this.numeroSerie = numeroSerie;
        this.validoDe = validoDe; this.validoAte = validoAte; this.atualizadoEm = Instant.now();
    }
}
