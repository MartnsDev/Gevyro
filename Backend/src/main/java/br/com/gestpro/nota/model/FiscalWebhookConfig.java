package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "fiscal_webhook_configs", uniqueConstraints =
        @UniqueConstraint(name = "uk_fiscal_webhook_empresa", columnNames = "empresa_id"))
@Getter @NoArgsConstructor
public class FiscalWebhookConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Lob @Column(name = "url_cifrada", nullable = false, columnDefinition = "BLOB") private byte[] urlCifrada;
    @Column(name = "url_nonce", nullable = false, length = 12) private byte[] urlNonce;
    @Lob @Column(name = "segredo_cifrado", nullable = false, columnDefinition = "BLOB") private byte[] segredoCifrado;
    @Column(name = "segredo_nonce", nullable = false, length = 12) private byte[] segredoNonce;
    @Column(name = "host_aprovado", nullable = false, length = 253) private String hostAprovado;
    @Column(name = "eventos", nullable = false, length = 255) private String eventos;
    @Column(nullable = false) private boolean ativo;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;

    public FiscalWebhookConfig(Long empresaId) { this.empresaId = empresaId; }
    public void atualizar(byte[] url, byte[] urlNonce, byte[] segredo, byte[] segredoNonce,
                          String host, String eventos, boolean ativo) {
        this.urlCifrada = url.clone(); this.urlNonce = urlNonce.clone();
        this.segredoCifrado = segredo.clone(); this.segredoNonce = segredoNonce.clone();
        this.hostAprovado = host; this.eventos = eventos; this.ativo = ativo;
        this.atualizadoEm = Instant.now();
    }
}
