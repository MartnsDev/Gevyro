package br.com.gestpro.nota.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

@Entity @Immutable
@Table(name = "xmls_fiscais", uniqueConstraints = @UniqueConstraint(
        name = "uk_xml_fiscal_documento_tipo", columnNames = {"documento_id", "tipo"}), indexes =
        @Index(name = "idx_xml_fiscal_empresa", columnList = "empresa_id, criado_em"))
@Getter @NoArgsConstructor
public class XmlFiscal {
    public enum Tipo { AUTORIZADO, EVENTO }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "documento_id", nullable = false) private Long documentoId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Tipo tipo;
    @Lob @Column(name = "conteudo_cifrado", nullable = false, columnDefinition = "LONGBLOB") private byte[] conteudoCifrado;
    @Column(nullable = false, length = 12) private byte[] nonce;
    @Column(name = "sha256", nullable = false, length = 64, unique = true) private String sha256;
    @Column(name = "layout_versao", nullable = false, length = 40) private String layoutVersao;
    @Column(nullable = false, length = 40) private String provider;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;

    public XmlFiscal(Long empresaId, Long documentoId, Tipo tipo, byte[] conteudoCifrado, byte[] nonce,
                     String sha256, String layoutVersao, String provider) {
        this.empresaId = empresaId; this.documentoId = documentoId; this.tipo = tipo;
        this.conteudoCifrado = conteudoCifrado.clone(); this.nonce = nonce.clone(); this.sha256 = sha256;
        this.layoutVersao = layoutVersao; this.provider = provider; this.criadoEm = Instant.now();
    }
}
