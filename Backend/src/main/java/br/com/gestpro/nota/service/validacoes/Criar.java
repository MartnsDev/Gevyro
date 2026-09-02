package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.dto.CriarNotaRequest;
import br.com.gestpro.nota.dto.ItemCalc;
import br.com.gestpro.nota.model.ItemNotaFiscal;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.service.FiscalSequenceService;
import br.com.gestpro.nota.config.NotaFiscalConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class Criar {

    private final NotaFiscalRepository notaRepo;
    private final BuscaPorId           buscaPorId;
    private final FiscalSequenceService fiscalSequenceService;
    private final NotaFiscalConfig.NotaFiscalProperties fiscalProperties;
    private final ConfiguracaoFiscalEmpresaRepository configuracaoRepository;

    // Ação principal

    @Transactional
    public Map<String, Object> criar(CriarNotaRequest request) {

        if(request.getEmpresaId()==null||request.getTipo()==null||request.getFormaPagamento()==null||request.getNaturezaOperacao()==null||request.getNaturezaOperacao().isBlank())
            throw new ApiException("Empresa, tipo, natureza da operação e pagamento são obrigatórios.",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");

        if (request.getItens() == null || request.getItens().isEmpty()) {
            throw new ApiException(
                    "A nota deve conter pelo menos um item.",
                    HttpStatus.BAD_REQUEST,
                    "/api/nota-fiscal"
            );
        }

        // 1. Instancia a nota (builder gerado pelo Lombok em NotaFiscal)
        ConfiguracaoFiscalEmpresa configuracao = configuracaoRepository.findByEmpresaId(request.getEmpresaId()).orElse(null);
        String seriePadrao = configuracao == null ? "1"
                : request.getTipo() == br.com.gestpro.nota.TipoNota.NFCE ? configuracao.getSerieNfce() : configuracao.getSerieNfe();
        boolean homologacao = configuracao == null ? fiscalProperties.isHomologacao()
                : configuracao.getAmbiente() == ConfiguracaoFiscalEmpresa.Ambiente.HOMOLOGACAO;
        NotaFiscal nota = NotaFiscal.builder()
                .empresaId(request.getEmpresaId())
                .clienteId(request.getClienteId())
                .clienteNome(request.getClienteNome())
                .clienteCpfCnpj(request.getClienteCpfCnpj())
                .tipo(request.getTipo())
                .naturezaOperacao(request.getNaturezaOperacao())
                .formaPagamento(request.getFormaPagamento())
                .serie(request.getSerie() != null ? request.getSerie() : seriePadrao)
                .valorFrete(coalesce(request.getValorFrete(), BigDecimal.ZERO))
                .valorDesconto(coalesce(request.getValorDesconto(), BigDecimal.ZERO))
                .informacoesAdicionais(request.getInformacoesAdicionais())
                .status(NotaFiscalStatus.DIGITACAO)
                .nfseCompetencia(request.getNfseCompetencia())
                .nfseCodigoMunicipioPrestacao(request.getNfseCodigoMunicipioPrestacao())
                .nfseCodigoTributacaoNacional(request.getNfseCodigoTributacaoNacional())
                .nfseOpcaoSimples(request.getNfseOpcaoSimples())
                .nfseRegimeEspecial(request.getNfseRegimeEspecial())
                .nfseTributacaoIssqn(request.getNfseTributacaoIssqn())
                .nfseRetencaoIssqn(request.getNfseRetencaoIssqn())
                .nfseAliquotaIssqn(request.getNfseAliquotaIssqn())
                .build();

        if (request.getTipo() == br.com.gestpro.nota.TipoNota.NFSE) validarNfse(request);

        // 2. Número sequencial por empresa/tipo/série
        Long proxNumero = fiscalSequenceService.reservar(
                request.getEmpresaId(), request.getTipo(), nota.getSerie(), homologacao);
        nota.setNumeroNota(proxNumero);

        // 3. Processa itens e acumula totais
        BigDecimal totalProdutos = BigDecimal.ZERO;
        BigDecimal totalIcms     = BigDecimal.ZERO;
        BigDecimal totalPis      = BigDecimal.ZERO;
        BigDecimal totalCofins   = BigDecimal.ZERO;
        int numItem = 1;

        for (ItemCalc ic : request.getItens()) {
            validarItem(ic,numItem, request.getTipo() == br.com.gestpro.nota.TipoNota.NFSE);
            ItemNotaFiscal item = ItemNotaFiscal.builder()
                    .produtoId(ic.getProdutoId())
                    .codigoProduto(ic.getCodigoProduto())
                    .descricao(ic.getDescricao())
                    .ncm(ic.getNcm())
                    .cfop(coalesceStr(ic.getCfop(), "5102"))
                    .unidade(coalesceStr(ic.getUnidade(), "UN"))
                    .quantidade(ic.getQuantidade())
                    .valorUnitario(ic.getValorUnitario())
                    .valorDesconto(coalesce(ic.getValorDesconto(), BigDecimal.ZERO))
                    .valorBruto(ic.getValorBruto())
                    .valorTotal(ic.getValorTotal())
                    .csosn(ic.getCsosn())
                    .cstIcms(ic.getCstIcms())
                    .icmsAliquota(coalesce(ic.getIcmsAliquota(), BigDecimal.ZERO))
                    .cstPis(ic.getCstPis())
                    .pisAliquota(coalesce(ic.getPisAliquota(), BigDecimal.ZERO))
                    .cstCofins(ic.getCstCofins())
                    .cofinsAliquota(coalesce(ic.getCofinsAliquota(), BigDecimal.ZERO))
                    .numeroItem(numItem++)
                    .build();

            // Cálculo de ICMS
            if (item.getIcmsAliquota().compareTo(BigDecimal.ZERO) > 0) {
                item.setIcmsBaseCalculo(item.getValorTotal());
                item.setIcmsValor(calcularImposto(item.getValorTotal(), item.getIcmsAliquota()));
                totalIcms = totalIcms.add(item.getIcmsValor());
            }

            // Cálculo de PIS
            if (item.getPisAliquota().compareTo(BigDecimal.ZERO) > 0) {
                item.setPisBaseCalculo(item.getValorTotal());
                item.setPisValor(calcularImposto(item.getValorTotal(), item.getPisAliquota()));
                totalPis = totalPis.add(item.getPisValor());
            }

            // Cálculo de COFINS
            if (item.getCofinsAliquota().compareTo(BigDecimal.ZERO) > 0) {
                item.setCofinsBaseCalculo(item.getValorTotal());
                item.setCofinsValor(calcularImposto(item.getValorTotal(), item.getCofinsAliquota()));
                totalCofins = totalCofins.add(item.getCofinsValor());
            }

            totalProdutos = totalProdutos.add(item.getValorTotal());

            // addItem garante consistência bidirecional (nota ↔ item)
            nota.addItem(item);
        }

        // 4. Consolida totais na nota
        nota.setValorProdutos(totalProdutos);
        nota.setValorIcms(totalIcms);
        nota.setValorPis(totalPis);
        nota.setValorCofins(totalCofins);
        nota.setValorTotal(totalProdutos
                .add(nota.getValorFrete())
                .subtract(nota.getValorDesconto()));
        if(nota.getValorFrete().signum()<0||nota.getValorDesconto().signum()<0||nota.getValorTotal().signum()<0)
            throw new ApiException("Frete e desconto devem ser positivos, e o desconto não pode superar o total.",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");

        // 5. Persiste (CascadeType.ALL persiste os itens automaticamente)
        log.info("Criando rascunho de nota fiscal: Tipo={} Empresa={} Número={}",
                nota.getTipo(), nota.getEmpresaId(), nota.getNumeroNota());
        NotaFiscal salva = notaRepo.save(nota);

        // 6. Retorna o Map detalhado via BuscaPorId
        return buscaPorId.buscarPorId(salva.getId());
    }

    // Helpers

    private BigDecimal calcularImposto(BigDecimal base, BigDecimal aliquota) {
        return base.multiply(aliquota)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void validarItem(ItemCalc item,int numero,boolean servico){
        if(item==null||item.getDescricao()==null||item.getDescricao().isBlank())throw new ApiException("Descrição obrigatória no item "+numero+".",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");
        String ncm=item.getNcm()==null?"":item.getNcm().replaceAll("\\D","");
        String cfop=item.getCfop()==null?"":item.getCfop().replaceAll("\\D","");
        if(!servico&&ncm.length()!=8)throw new ApiException("NCM deve conter 8 dígitos no item "+numero+".",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");
        if(!servico&&cfop.length()!=4)throw new ApiException("CFOP deve conter 4 dígitos no item "+numero+".",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");
        if(item.getQuantidade()==null||item.getQuantidade().signum()<=0)throw new ApiException("Quantidade deve ser maior que zero no item "+numero+".",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");
        if(item.getValorUnitario()==null||item.getValorUnitario().signum()<0)throw new ApiException("Valor unitário inválido no item "+numero+".",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");
        if(item.getValorDesconto()!=null&&(item.getValorDesconto().signum()<0||item.getValorDesconto().compareTo(item.getValorBruto())>0))throw new ApiException("Desconto inválido no item "+numero+".",HttpStatus.BAD_REQUEST,"/api/nota-fiscal");
        item.setNcm(ncm);item.setCfop(cfop);
    }

    private void validarNfse(CriarNotaRequest r) {
        if (r.getItens().size() != 1) throw new ApiException("A DPS Nacional aceita inicialmente um serviço por nota.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal");
        if (r.getNfseCompetencia() == null || r.getNfseCodigoMunicipioPrestacao() == null
                || !r.getNfseCodigoMunicipioPrestacao().matches("[0-9]{7}")
                || r.getNfseCodigoTributacaoNacional() == null || !r.getNfseCodigoTributacaoNacional().matches("[0-9]{6}"))
            throw new ApiException("Competência, município e código de tributação nacional são obrigatórios para NFS-e.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal");
        if (r.getNfseOpcaoSimples() == null || r.getNfseRegimeEspecial() == null
                || r.getNfseTributacaoIssqn() == null || r.getNfseRetencaoIssqn() == null)
            throw new ApiException("O tratamento tributário da NFS-e deve ser confirmado.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal");
        if (r.getNfseOpcaoSimples() < 1 || r.getNfseOpcaoSimples() > 3
                || r.getNfseRegimeEspecial() < 0
                || (r.getNfseRegimeEspecial() > 6 && r.getNfseRegimeEspecial() != 9)
                || r.getNfseTributacaoIssqn() < 1 || r.getNfseTributacaoIssqn() > 4
                || r.getNfseRetencaoIssqn() < 1 || r.getNfseRetencaoIssqn() > 3
                || (r.getNfseAliquotaIssqn() != null && (r.getNfseAliquotaIssqn().signum() < 0
                || r.getNfseAliquotaIssqn().compareTo(new BigDecimal("9.99")) > 0)))
            throw new ApiException("Valores de regime ou ISSQN fora do domínio oficial da DPS.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal");
    }

    private BigDecimal coalesce(BigDecimal val, BigDecimal fallback) {
        return val != null ? val : fallback;
    }

    private String coalesceStr(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val.trim() : fallback;
    }
}
