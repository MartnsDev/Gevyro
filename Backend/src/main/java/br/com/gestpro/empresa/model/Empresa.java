package br.com.gestpro.empresa.model;

import br.com.gestpro.plano.TipoPlano;
import br.com.gestpro.auth.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomeFantasia;

    @Column(name = "cnpj", unique = true)
    private String cnpj;

    private String logotipoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario dono;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_plano", nullable = false)
    private TipoPlano plano;

    // A base de produção usa historicamente a coluna `ativa`. Manter o nome
    // explícito evita o Hibernate criar `ativo` em paralelo e deixar a coluna
    // legada obrigatória sem valor durante o INSERT.
    @Column(name = "ativa", nullable = false)
    private Boolean ativo = true;

    /**
     * Compatibilidade temporária com a coluna duplicada criada por uma versão
     * anterior do mapeamento em bancos que já estavam em produção.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "ativo", nullable = false)
    private Boolean ativoCompatibilidade = true;

    @PrePersist
    @PreUpdate
    private void sincronizarColunasDeStatus() {
        if (ativo == null) {
            ativo = true;
        }
        ativoCompatibilidade = ativo;
    }

    // Dados CNPJ
    @Column(name = "razao_social")
    private String razaoSocial;

    private String cep;
    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;
    private String uf;
    private String telefone;
}
