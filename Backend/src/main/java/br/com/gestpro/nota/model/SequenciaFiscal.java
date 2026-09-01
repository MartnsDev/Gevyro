package br.com.gestpro.nota.model;

import br.com.gestpro.nota.TipoNota;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sequencias_fiscais", uniqueConstraints = @UniqueConstraint(
        name = "uk_sequencia_fiscal_empresa_tipo_serie_ambiente",
        columnNames = {"empresa_id", "tipo", "serie", "ambiente"}))
@Getter
@NoArgsConstructor
public class SequenciaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoNota tipo;

    @Column(nullable = false, length = 3)
    private String serie;

    @Column(nullable = false, length = 15)
    private String ambiente;

    @Column(name = "proximo_numero", nullable = false)
    private Long proximoNumero;

    public SequenciaFiscal(Long empresaId, TipoNota tipo, String serie, String ambiente, Long proximoNumero) {
        this.empresaId = empresaId;
        this.tipo = tipo;
        this.serie = serie;
        this.ambiente = ambiente;
        this.proximoNumero = proximoNumero;
    }

    public Long reservar() {
        long reservado = proximoNumero;
        proximoNumero = Math.addExact(proximoNumero, 1L);
        return reservado;
    }
}
