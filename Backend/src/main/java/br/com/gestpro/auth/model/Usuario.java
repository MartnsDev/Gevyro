package br.com.gestpro.auth.model;

import br.com.gestpro.plano.StatusAcesso;
import br.com.gestpro.plano.TipoPlano;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    private String senha;

    @Column(name = "foto_google")
    private String foto;

    @Column
    private String fotoUpload;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_plano", nullable = false)
    private TipoPlano tipoPlano = TipoPlano.EXPERIMENTAL;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_primeiro_login")
    private LocalDateTime dataPrimeiroLogin;

    @Column(name = "data_assinatura_plus")
    private LocalDateTime dataAssinaturaPlus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_acesso", nullable = false)
    private StatusAcesso statusAcesso = StatusAcesso.ATIVO;

    @Column(nullable = false)
    private boolean emailConfirmado = false;

    @Column
    @JsonIgnore
    private String tokenConfirmacao;

    @Column
    private LocalDateTime dataEnvioConfirmacao;

    @Column
    @JsonIgnore
    private String codigoRecuperacao;

    @Column(name = "login_google", nullable = false)
    private boolean loginGoogle = false;

    // Preferências de Notificação
    @Column(name = "email_vendas", nullable = false)
    private boolean emailVendas = false;

    @Column(name = "email_relatorios", nullable = false)
    private boolean emailRelatorios = false;

    @Column(name = "alerta_estoque_zerado", nullable = false)
    private boolean alertaEstoqueZerado = true;

    @Column(name = "alerta_vencimento_plano", nullable = false)
    private boolean alertaVencimentoPlano = true;

    // Regras automáticas de persistência
    @PrePersist
    public void prePersist() {
        if (dataCriacao == null) dataCriacao = LocalDateTime.now();
        // O primeiro login no Experimental começa na criação da conta
        if (tipoPlano == TipoPlano.EXPERIMENTAL && dataPrimeiroLogin == null) {
            dataPrimeiroLogin = LocalDateTime.now();
        }
        if (statusAcesso == null) statusAcesso = StatusAcesso.ATIVO;
        if (tipoPlano == null) tipoPlano = TipoPlano.EXPERIMENTAL;
    }

}
