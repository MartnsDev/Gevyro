package br.com.gestpro.venda.service;

import br.com.gestpro.venda.dto.RegistrarVendaDTO;
import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.caixa.model.Caixa;
import br.com.gestpro.cliente.model.Cliente;
import br.com.gestpro.produto.model.Produto;
import br.com.gestpro.venda.model.ItemVenda;
import br.com.gestpro.venda.model.Venda;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.caixa.repository.CaixaRepository;
import br.com.gestpro.cliente.repository.ClienteRepository;
import br.com.gestpro.produto.repository.ProdutoRepository;
import br.com.gestpro.venda.repository.VendaRepository;
import br.com.gestpro.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VendaServiceImpl implements VendaServiceInterface {

    private static final Logger log = LoggerFactory.getLogger(VendaServiceImpl.class);

    private final VendaRepository    vendaRepository;
    private final ProdutoRepository  produtoRepository;
    private final UsuarioRepository  usuarioRepository;
    private final CaixaRepository    caixaRepository;
    private final ClienteRepository  clienteRepository;

    @Override
    @Transactional
    public Venda registrarVenda(RegistrarVendaDTO dto) {
        if (dto.getItens() == null || dto.getItens().isEmpty())
            throw new ApiException("Nenhum item enviado", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");

        Usuario usuario = usuarioRepository.findByEmail(dto.getEmailUsuario())
                .orElseThrow(() -> new ApiException("Usuário não encontrado", HttpStatus.NOT_FOUND, "/api/v1/vendas/registrar"));

        Caixa caixa = caixaRepository.findById(dto.getIdCaixa())
                .orElseThrow(() -> new ApiException("Caixa não encontrado", HttpStatus.NOT_FOUND, "/api/v1/vendas/registrar"));

        if (!caixa.getUsuario().getId().equals(usuario.getId()))
            throw new ApiException("Sem permissão para este caixa.", HttpStatus.FORBIDDEN, "/api/v1/vendas/registrar");
        if (caixa.getEmpresa() == null || !Boolean.TRUE.equals(caixa.getEmpresa().getAtivo()))
            throw new ApiException("A empresa deste caixa está arquivada.", HttpStatus.CONFLICT, "/api/v1/vendas/registrar");

        if (!caixa.getAberto())
            throw new ApiException("O caixa está fechado.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");

        Cliente cliente = null;
        if (dto.getIdCliente() != null)
            cliente = clienteRepository.findById(dto.getIdCliente())
                    .orElseThrow(() -> new ApiException("Cliente não encontrado", HttpStatus.NOT_FOUND, "/api/v1/vendas/registrar"));
        if (cliente != null && (cliente.getEmpresa() == null
                || !cliente.getEmpresa().getId().equals(caixa.getEmpresa().getId())))
            throw new ApiException("Cliente não pertence à empresa deste caixa.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");

        Venda venda = new Venda();
        venda.setUsuario(usuario);
        venda.setCaixa(caixa);
        venda.setCliente(cliente);
        venda.setFormaPagamento(dto.getFormaPagamento());
        venda.setFormaPagamento2(dto.getFormaPagamento2());
        venda.setValorPagamento2(dto.getValorPagamento2());
        venda.setObservacao(dto.getObservacao());
        venda.setCancelada(false);
        if (caixa.getEmpresa() != null) venda.setEmpresa(caixa.getEmpresa());

        List<ItemVenda> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (RegistrarVendaDTO.ItemVendaDTO itemDTO : dto.getItens()) {
            Produto produto = produtoRepository.findByIdForUpdate(itemDTO.getIdProduto())
                    .orElseThrow(() -> new ApiException("Produto não encontrado: " + itemDTO.getIdProduto(), HttpStatus.NOT_FOUND, "/api/v1/vendas/registrar"));

            if (produto.getEmpresa() == null || !produto.getEmpresa().getId().equals(caixa.getEmpresa().getId()))
                throw new ApiException("Produto não pertence à empresa deste caixa.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");

            int qtd = itemDTO.getQuantidade() != null ? itemDTO.getQuantidade() : 1;

            if (produto.getQuantidadeEstoque() < qtd)
                throw new ApiException("Estoque insuficiente para: " + produto.getNome(), HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");

            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - qtd);
            produtoRepository.save(produto);

            ItemVenda iv = new ItemVenda();
            iv.setVenda(venda);
            iv.setProduto(produto);
            iv.setQuantidade(qtd);
            iv.setPrecoUnitario(produto.getPreco());
            iv.calcularSubtotal();

            total = total.add(iv.getSubtotal());
            itens.add(iv);
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0)
            throw new ApiException("A venda precisa ter valor maior que zero.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");

        venda.setItens(itens);

        //  Desconto normalizado — null nunca chega ao banco
        BigDecimal desconto = dto.getDesconto() != null
                ? dto.getDesconto().max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        if (desconto.compareTo(total) >= 0)
            throw new ApiException("O desconto deve ser menor que o subtotal da venda.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");

        venda.setTotal(total);
        venda.setDesconto(desconto);
        venda.setValorFinal(total.subtract(desconto).max(BigDecimal.ZERO));

        if (dto.getFormaPagamento2() != null) {
            if (dto.getFormaPagamento2() == dto.getFormaPagamento())
                throw new ApiException("As formas do pagamento dividido devem ser diferentes.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");
            if (dto.getValorPagamento2() == null || dto.getValorPagamento2().signum() <= 0
                    || dto.getValorPagamento2().compareTo(venda.getValorFinal()) >= 0)
                throw new ApiException("O segundo pagamento deve ser maior que zero e menor que o total da venda.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");
        } else if (dto.getValorPagamento2() != null && dto.getValorPagamento2().signum() != 0) {
            throw new ApiException("Informe a segunda forma de pagamento para o valor dividido.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/registrar");
        }

        // Troco para pagamento em dinheiro
        if (dto.getValorRecebido() != null && dto.getValorRecebido().compareTo(BigDecimal.ZERO) > 0) {
            venda.setValorRecebido(dto.getValorRecebido());
            venda.setTroco(dto.getValorRecebido().subtract(venda.getValorFinal()).max(BigDecimal.ZERO));
        }

        Venda salvo = vendaRepository.save(venda);

        BigDecimal novoTotal = caixa.getTotalVendas() != null
                ? caixa.getTotalVendas().add(salvo.getValorFinal())
                : salvo.getValorFinal();
        caixa.setTotalVendas(novoTotal);
        caixaRepository.save(caixa);

        log.info("Venda id={} registrada. empresa={}, valorFinal={}, desconto={}",
                salvo.getId(), caixa.getEmpresa() != null ? caixa.getEmpresa().getNomeFantasia() : "N/A",
                salvo.getValorFinal(), salvo.getDesconto());

        return salvo;
    }

    @Override
    @Transactional
    public Venda cancelarVenda(Long id, String motivo, String emailUsuario) {
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Venda não encontrada", HttpStatus.NOT_FOUND, "/api/v1/vendas/" + id));

        if (!venda.getUsuario().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão.", HttpStatus.FORBIDDEN, "/api/v1/vendas/" + id);

        if (Boolean.TRUE.equals(venda.getCancelada()))
            throw new ApiException("Venda já cancelada.", HttpStatus.BAD_REQUEST, "/api/v1/vendas/" + id);

        // Devolve estoque
        venda.getItens().forEach(item -> {
            Produto p = produtoRepository.findByIdForUpdate(item.getProduto().getId())
                    .orElseThrow(() -> new ApiException("Produto não encontrado", HttpStatus.NOT_FOUND, "/api/v1/vendas/" + id));
            p.setQuantidadeEstoque(p.getQuantidadeEstoque() + item.getQuantidade());
            produtoRepository.save(p);
        });

        // Deduz do total do caixa
        Caixa caixa = venda.getCaixa();
        if (caixa.getTotalVendas() != null)
            caixa.setTotalVendas(caixa.getTotalVendas().subtract(venda.getValorFinal()).max(BigDecimal.ZERO));
        caixaRepository.save(caixa);

        venda.setCancelada(true);
        venda.setDataCancelamento(LocalDateTime.now());
        venda.setMotivoCancelamento(motivo);

        return vendaRepository.save(venda);
    }

    @Override
    @Transactional
    public Venda editarObservacao(Long id, String observacao, String emailUsuario) {
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Venda não encontrada", HttpStatus.NOT_FOUND, "/api/v1/vendas/" + id));

        if (!venda.getUsuario().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão.", HttpStatus.FORBIDDEN, "/api/v1/vendas/" + id);

        venda.setObservacao(observacao);
        return vendaRepository.save(venda);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Venda> listarPorCaixa(Long idCaixa, String emailUsuario) {
        Caixa caixa = caixaRepository.findById(idCaixa)
                .orElseThrow(() -> new ApiException("Caixa não encontrado", HttpStatus.NOT_FOUND, "/api/v1/vendas/caixa/" + idCaixa));
        validarUsuarioDaVenda(caixa.getUsuario().getEmail(), emailUsuario, "/api/v1/vendas/caixa/" + idCaixa);
        return vendaRepository.findByCaixaId(idCaixa);
    }

    @Override
    @Transactional(readOnly = true)
    public Venda buscarPorId(Long id, String emailUsuario) {
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Venda não encontrada", HttpStatus.NOT_FOUND, "/api/v1/vendas/"));
        validarUsuarioDaVenda(venda.getUsuario().getEmail(), emailUsuario, "/api/v1/vendas/" + id);
        return venda;
    }

    private void validarUsuarioDaVenda(String emailProprietario, String emailUsuario, String path) {
        if (!emailProprietario.equalsIgnoreCase(emailUsuario))
            throw new ApiException("Sem permissão.", HttpStatus.FORBIDDEN, path);
    }
}
