package br.com.gestpro.marketplace.model;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.pedidos.CanalVenda;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * Credenciais OAuth de um marketplace vinculadas a uma empresa.
 * Uma empresa pode ter no máximo uma conexão ativa por marketplace.
 */
@Entity
@Table(
        name = "marketplace_connection",
        uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "marketplace"})
)
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MarketplaceConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    /**
     * Apenas SHOPEE e MERCADO_LIVRE são suportados neste módulo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CanalVenda marketplace;

    /**
     * ID do seller/vendedor na plataforma — usado para identificar
     * a qual empresa pertence um webhook recebido.
     */
    @Column(name = "seller_id", nullable = false, length = 100)
    private String sellerId;

    @Column(name = "access_token", nullable = false, length = 2000)
    @JsonIgnore
    private String accessToken;

    /** Presente apenas em marketplaces que suportam renovação (ML, Shopee) */
    @Column(name = "refresh_token", length = 2000)
    @JsonIgnore
    private String refreshToken;

    /** Momento em que o access_token expira — null = não expira */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isTokenExpired() {
        return tokenExpiresAt != null && LocalDateTime.now().isAfter(tokenExpiresAt);
    }
}
