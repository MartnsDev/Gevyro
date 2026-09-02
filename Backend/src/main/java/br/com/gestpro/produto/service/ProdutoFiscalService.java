package br.com.gestpro.produto.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.produto.dto.*;
import br.com.gestpro.produto.model.*;
import br.com.gestpro.produto.repository.*;
import br.com.gestpro.nota.service.FiscalAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProdutoFiscalService {
    private final ProdutoRepository produtoRepository;
    private final ProdutoConfiguracaoFiscalRepository repository;
    private final FiscalAuditService auditService;

    @Transactional(readOnly=true)
    public Long empresaIdAutorizada(Long produtoId, String ator) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> erro("Produto não encontrado.", HttpStatus.NOT_FOUND, produtoId));
        validarAcesso(produto, ator);
        return produto.getEmpresa().getId();
    }

    @Transactional
    public ProdutoFiscalResponse criarVersao(Long produtoId, ProdutoFiscalRequest request, String ator) {
        Produto produto = produtoRepository.findByIdForUpdate(produtoId)
                .orElseThrow(() -> erro("Produto não encontrado.", HttpStatus.NOT_FOUND, produtoId));
        validarAcesso(produto, ator);
        normalizar(request);
        validarRegras(request, produtoId);

        ProdutoConfiguracaoFiscal anterior = repository.findTopByProdutoIdOrderByVersaoDesc(produtoId).orElse(null);
        int versao = anterior == null ? 1 : anterior.getVersao() + 1;
        if (anterior != null) {
            if (!request.getVigenciaInicio().isAfter(anterior.getVigenciaInicio()))
                throw erro("A nova vigência deve começar depois da versão fiscal anterior.", HttpStatus.CONFLICT, produtoId);
            anterior.encerrar(request.getVigenciaInicio().minusDays(1));
        }
        ProdutoConfiguracaoFiscal config = repository.save(new ProdutoConfiguracaoFiscal(produtoId,
                produto.getEmpresa().getId(), versao, request.getVigenciaInicio(), request.getNcm(), vazioParaNull(request.getCest()),
                request.getOrigem(), request.getUnidadeComercial(), request.getUnidadeTributavel(), vazioParaNull(request.getGtin()),
                request.getCfopPadrao(), vazioParaNull(request.getCsosn()), vazioParaNull(request.getCstIcms()),
                vazioParaNull(request.getCstIpi()), request.getCstPis(), request.getCstCofins(),
                vazioParaNull(request.getCstIbsCbs()), vazioParaNull(request.getCclassTrib()), ator));
        auditService.registrar(produto.getEmpresa().getId(), null, "PRODUTO_FISCAL_VERSIONADO", ator, "SUCESSO",
                "produtoId=" + produtoId + ",versao=" + versao + ",vigencia=" + request.getVigenciaInicio());
        return ProdutoFiscalResponse.of(config);
    }

    @Transactional(readOnly=true)
    public ProdutoFiscalResponse vigente(Long produtoId, LocalDate data, String ator) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> erro("Produto não encontrado.", HttpStatus.NOT_FOUND, produtoId));
        validarAcesso(produto, ator);
        List<ProdutoConfiguracaoFiscal> configs = repository.vigentes(produtoId, data == null ? LocalDate.now() : data);
        if (configs.isEmpty()) throw erro("Produto sem configuração fiscal vigente.", HttpStatus.NOT_FOUND, produtoId);
        if (configs.size() > 1) throw erro("Há sobreposição de configurações fiscais; emissão bloqueada.", HttpStatus.CONFLICT, produtoId);
        return ProdutoFiscalResponse.of(configs.get(0));
    }

    @Transactional(readOnly=true)
    public List<ProdutoFiscalResponse> historico(Long produtoId, String ator) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> erro("Produto não encontrado.", HttpStatus.NOT_FOUND, produtoId));
        validarAcesso(produto, ator);
        return repository.findByProdutoIdOrderByVersaoDesc(produtoId).stream().map(ProdutoFiscalResponse::of).toList();
    }

    private void validarAcesso(Produto produto, String ator) {
        if (produto.getEmpresa() == null || produto.getEmpresa().getDono() == null
                || !produto.getEmpresa().getDono().getEmail().equalsIgnoreCase(ator))
            throw erro("Sem permissão para a configuração fiscal deste produto.", HttpStatus.FORBIDDEN, produto.getId());
        if (!Boolean.TRUE.equals(produto.getEmpresa().getAtivo()))
            throw erro("A empresa está arquivada.", HttpStatus.CONFLICT, produto.getId());
    }

    private void normalizar(ProdutoFiscalRequest r) {
        r.setNcm(digitos(r.getNcm())); r.setCest(digitos(r.getCest())); r.setGtin(digitos(r.getGtin()));
        r.setCfopPadrao(digitos(r.getCfopPadrao())); r.setCsosn(digitos(r.getCsosn())); r.setCstIcms(digitos(r.getCstIcms()));
        r.setCstIpi(digitos(r.getCstIpi())); r.setCstPis(digitos(r.getCstPis())); r.setCstCofins(digitos(r.getCstCofins()));
        r.setCstIbsCbs(digitos(r.getCstIbsCbs())); r.setCclassTrib(digitos(r.getCclassTrib()));
        r.setUnidadeComercial(r.getUnidadeComercial().trim().toUpperCase(Locale.ROOT));
        r.setUnidadeTributavel(r.getUnidadeTributavel().trim().toUpperCase(Locale.ROOT));
    }
    private void validarRegras(ProdutoFiscalRequest r, Long id) {
        if (r.getVigenciaInicio().isBefore(LocalDate.now())) throw erro("A vigência não pode começar no passado.", HttpStatus.BAD_REQUEST, id);
        if ((r.getCsosn()==null || r.getCsosn().isBlank()) == (r.getCstIcms()==null || r.getCstIcms().isBlank()))
            throw erro("Informe exatamente um entre CSOSN e CST ICMS, conforme o regime tributário.", HttpStatus.BAD_REQUEST, id);
    }
    private String digitos(String s) { return s == null ? null : s.replaceAll("\\D", ""); }
    private String vazioParaNull(String s) { return s == null || s.isBlank() ? null : s; }
    private ApiException erro(String msg, HttpStatus status, Long id) { return new ApiException(msg,status,"/api/v1/produtos/"+id+"/fiscal"); }
}
