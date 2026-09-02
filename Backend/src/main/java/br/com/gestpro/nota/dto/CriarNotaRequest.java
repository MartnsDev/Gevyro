package br.com.gestpro.nota.dto;


import br.com.gestpro.nota.FormaPagamento;
import br.com.gestpro.nota.TipoNota;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriarNotaRequest {
    private Long empresaId;
    private Long clienteId;
    private String clienteNome;
    private String clienteCpfCnpj;
    private TipoNota tipo;
    private String naturezaOperacao;
    private FormaPagamento formaPagamento;
    private String serie;
    private BigDecimal valorFrete;
    private BigDecimal valorDesconto;
    private String informacoesAdicionais;
    private List<ItemCalc> itens;
    private Boolean emitirImediatamente;
    private LocalDate nfseCompetencia;
    private String nfseCodigoMunicipioPrestacao;
    private String nfseCodigoTributacaoNacional;
    private Integer nfseOpcaoSimples;
    private Integer nfseRegimeEspecial;
    private Integer nfseTributacaoIssqn;
    private Integer nfseRetencaoIssqn;
    private BigDecimal nfseAliquotaIssqn;
}
