package br.com.gestpro.nota.dto;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.DocumentoFiscalAmbiente;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * Objeto de transporte para filtros de pesquisa e paginação.
 * Utilizado no endpoint GET /api/nota-fiscal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterNotaFiscalDTO {

    // Filtros de Identidade
    private Long empresaId;
    private NotaFiscalStatus status;
    private TipoNota tipo;
    private DocumentoFiscalAmbiente ambiente;
    private String clienteNome;
    private Long numero;
    private String serie;
    private BigDecimal valorMin;
    private BigDecimal valorMax;

    // Filtros de Período (Formato esperado: yyyy-MM-dd)
    private String dataInicio;
    private String dataFim;

    // Paginação (Padrão: página 1, limite 20)
    @Builder.Default
    private Integer page = 1;

    @Builder.Default
    private Integer limit = 20;

}
