package br.com.gestpro.nota.model;

import br.com.gestpro.nota.RegimeTributario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "configuracoes_fiscais", uniqueConstraints =
        @UniqueConstraint(name = "uk_configuracao_fiscal_empresa", columnNames = "empresa_id"))
@Getter @NoArgsConstructor
public class ConfiguracaoFiscalEmpresa {
    public enum Ambiente { HOMOLOGACAO, PRODUCAO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "inscricao_estadual", length = 20) private String inscricaoEstadual;
    @Enumerated(EnumType.STRING) @Column(name = "regime_tributario", nullable = false, length = 40)
    private RegimeTributario regimeTributario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private Ambiente ambiente;
    @Column(name = "serie_nfe", nullable = false, length = 3) private String serieNfe;
    @Column(name = "serie_nfce", nullable = false, length = 3) private String serieNfce;
    @Column(name = "csc_id", length = 20) private String cscId;
    @Lob @Column(name = "csc_cifrado", columnDefinition = "BLOB") private byte[] cscCifrado;
    @Column(name = "csc_nonce", length = 12) private byte[] cscNonce;
    @Column(name = "fiscal_habilitado", nullable = false) private boolean fiscalHabilitado;
    @Column(name = "nfe_habilitada", nullable = false) private boolean nfeHabilitada;
    @Column(name = "nfce_habilitada", nullable = false) private boolean nfceHabilitada;
    @Column(name = "nfse_habilitada", nullable = false) private boolean nfseHabilitada;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;

    public ConfiguracaoFiscalEmpresa(Long empresaId) { this.empresaId = empresaId; }

    public void atualizar(String inscricaoEstadual, RegimeTributario regime, Ambiente ambiente,
                          String serieNfe, String serieNfce, String cscId,
                          byte[] cscCifrado, byte[] cscNonce, boolean substituirCsc,
                          boolean fiscalHabilitado, boolean nfeHabilitada,
                          boolean nfceHabilitada, boolean nfseHabilitada) {
        this.inscricaoEstadual = inscricaoEstadual;
        this.regimeTributario = regime;
        this.ambiente = ambiente;
        this.serieNfe = serieNfe;
        this.serieNfce = serieNfce;
        this.cscId = cscId;
        if (substituirCsc) {
            this.cscCifrado = cscCifrado == null ? null : cscCifrado.clone();
            this.cscNonce = cscNonce == null ? null : cscNonce.clone();
        }
        this.fiscalHabilitado = fiscalHabilitado;
        this.nfeHabilitada = nfeHabilitada;
        this.nfceHabilitada = nfceHabilitada;
        this.nfseHabilitada = nfseHabilitada;
        this.atualizadoEm = Instant.now();
    }
}
