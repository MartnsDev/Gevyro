package br.com.gestpro.nota.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * DTO que carrega as informações consolidadas para o Dashboard do ERP.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstatisticasResponse {

    private long totalAutorizadas;
    private long totalRejeitadas;
    private long totalCanceladas;
    private long totalAguardando;
    private long totalErros;
    private BigDecimal valorTotalMes;

}
