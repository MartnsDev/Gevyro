package br.com.gestpro.nota.dto;

import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.DocumentoFiscalAmbiente;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaFiscalResumoResponse {
    private Long id;
    private String numeroNota;
    private String serie;
    private TipoNota tipo;
    private NotaFiscalStatus status;
    private DocumentoFiscalAmbiente ambiente;
    private String clienteNome;
    private String chaveAcesso;
    private BigDecimal valorTotal;
    private LocalDateTime dataEmissao;
    private String protocolo;
}
