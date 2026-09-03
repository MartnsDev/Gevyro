package br.com.gestpro.nota.model;

import br.com.gestpro.nota.FiscalRole;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fiscal_company_access", uniqueConstraints = @UniqueConstraint(name = "uk_fiscal_access_company_user", columnNames = {"empresa_id", "usuario_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FiscalCompanyAccess {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private FiscalRole role;
    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;
}
