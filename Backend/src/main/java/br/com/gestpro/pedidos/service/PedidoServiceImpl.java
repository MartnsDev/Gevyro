package br.com.gestpro.pedidos.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.cliente.model.Cliente;
import br.com.gestpro.cliente.repository.ClienteRepository;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.pedidos.CanalVenda;
import br.com.gestpro.pedidos.Repository.PedidoRepository;
import br.com.gestpro.pedidos.dto.ItemPedido;
import br.com.gestpro.pedidos.dto.RegistrarPedidoDTO;
import br.com.gestpro.pedidos.dto.StatusPedido;
import br.com.gestpro.pedidos.model.Pedido;
import br.com.gestpro.produto.model.Produto;
import br.com.gestpro.produto.repository.ProdutoRepository;
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
public class PedidoServiceImpl implements PedidoServiceInterface {

    private static final Logger log = LoggerFactory.getLogger(PedidoServiceImpl.class);
    private static final String PATH = "/api/v1/pedidos";

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository  produtoRepository;
    private final UsuarioRepository  usuarioRepository;
    private final EmpresaRepository  empresaRepository;
    private final ClienteRepository  clienteRepository;


    @Override
    @Transactional
    public Pedido registrarPedido(RegistrarPedidoDTO dto) {
        if (dto.getItens() == null || dto.getItens().isEmpty())
            throw new ApiException("Nenhum item enviado", HttpStatus.BAD_REQUEST, PATH);

        Usuario usuario = usuarioRepository.findByEmail(dto.getEmailUsuario())
                .orElseThrow(() -> new ApiException("Usuário não encontrado", HttpStatus.NOT_FOUND, PATH));

        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new ApiException("Empresa não encontrada", HttpStatus.NOT_FOUND, PATH));

        if (!empresa.getDono().getId().equals(usuario.getId()))
            throw new ApiException("Sem permissão para esta empresa.", HttpStatus.FORBIDDEN, PATH);
        if (!Boolean.TRUE.equals(empresa.getAtivo()))
            throw new ApiException("Esta empresa está arquivada.", HttpStatus.CONFLICT, PATH);

        Cliente cliente = null;
        if (dto.getIdCliente() != null)
            cliente = clienteRepository.findById(dto.getIdCliente())
                    .orElseThrow(() -> new ApiException("Cliente não encontrado", HttpStatus.NOT_FOUND, PATH));
        if (cliente != null && (cliente.getEmpresa() == null
                || !cliente.getEmpresa().getId().equals(empresa.getId())))
            throw new ApiException("Cliente não pertence a esta empresa.", HttpStatus.BAD_REQUEST, PATH);

        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setEmpresa(empresa);
        pedido.setCliente(cliente);
        pedido.setFormaPagamento(dto.getFormaPagamento());
        pedido.setCanalVenda(dto.getCanalVenda() != null ? dto.getCanalVenda() : CanalVenda.OUTRO);
        pedido.setContaDestino(dto.getContaDestino());
        pedido.setEnderecoEntrega(dto.getEnderecoEntrega());
        pedido.setCustoFrete(dto.getCustoFrete() != null ? dto.getCustoFrete() : BigDecimal.ZERO);
        pedido.setObservacao(dto.getObservacao());
        pedido.setStatus(StatusPedido.CONFIRMADO);

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (RegistrarPedidoDTO.ItemPedidoDTO itemDTO : dto.getItens()) {
            Produto produto = produtoRepository.findByIdForUpdate(itemDTO.getIdProduto())
                    .orElseThrow(() -> new ApiException(
                            "Produto não encontrado: " + itemDTO.getIdProduto(), HttpStatus.NOT_FOUND, PATH));

            if (produto.getEmpresa() == null || !produto.getEmpresa().getId().equals(empresa.getId()))
                throw new ApiException(
                        "Produto '" + produto.getNome() + "' não pertence a esta empresa.",
                        HttpStatus.BAD_REQUEST, PATH);

            int qtd = itemDTO.getQuantidade();
            if (produto.getQuantidadeEstoque() < qtd)
                throw new ApiException(
                        "Estoque insuficiente para: " + produto.getNome(), HttpStatus.BAD_REQUEST, PATH);

            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - qtd);
            produtoRepository.save(produto);

            ItemPedido ip = new ItemPedido();
            ip.setPedido(pedido);
            ip.setProduto(produto);
            ip.setQuantidade(qtd);
            ip.setPrecoUnitario(produto.getPreco());
            ip.calcularSubtotal();

            total = total.add(ip.getSubtotal());
            itens.add(ip);
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0)
            throw new ApiException("O pedido precisa ter valor maior que zero.", HttpStatus.BAD_REQUEST, PATH);

        pedido.setItens(itens);

        BigDecimal desconto = dto.getDesconto() != null
                ? dto.getDesconto().max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        if (desconto.compareTo(total) >= 0)
            throw new ApiException("O desconto deve ser menor que o subtotal do pedido.", HttpStatus.BAD_REQUEST, PATH);

        pedido.setValorTotal(total);
        pedido.setDesconto(desconto);
        pedido.setValorFinal(
                total.subtract(desconto)
                        .add(pedido.getCustoFrete())
                        .max(BigDecimal.ZERO)
        );

        Pedido salvo = pedidoRepository.save(pedido);
        log.info("Pedido id={} registrado. empresa={}, canal={}, valorFinal={}",
                salvo.getId(), empresa.getNomeFantasia(), salvo.getCanalVenda(), salvo.getValorFinal());
        return salvo;
    }


    @Override
    @Transactional
    public Pedido atualizarStatus(Long id, StatusPedido novoStatus, String emailUsuario) {
        Pedido pedido = buscarComPermissao(id, emailUsuario);
        if (pedido.getStatus() == StatusPedido.CANCELADO)
            throw new ApiException("Pedido cancelado não pode ser alterado.", HttpStatus.BAD_REQUEST, PATH);
        if (novoStatus == StatusPedido.CANCELADO)
            return cancelarPedido(id, null, emailUsuario);
        if (!transicaoPermitida(pedido.getStatus(), novoStatus))
            throw new ApiException("Transição de status não permitida.", HttpStatus.BAD_REQUEST, PATH);
        pedido.setStatus(novoStatus);
        return pedidoRepository.save(pedido);
    }


    @Override
    @Transactional
    public Pedido cancelarPedido(Long id, String motivo, String emailUsuario) {
        Pedido pedido = buscarComPermissao(id, emailUsuario);
        if (pedido.getStatus() == StatusPedido.CANCELADO)
            throw new ApiException("Pedido já cancelado.", HttpStatus.BAD_REQUEST, PATH);
        if (pedido.getStatus() == StatusPedido.ENTREGUE)
            throw new ApiException("Pedido entregue não pode ser cancelado.", HttpStatus.BAD_REQUEST, PATH);

        // Devolve estoque
        pedido.getItens().forEach(item -> {
            Produto p = produtoRepository.findByIdForUpdate(item.getProduto().getId())
                    .orElseThrow(() -> new ApiException("Produto não encontrado", HttpStatus.NOT_FOUND, PATH));
            p.setQuantidadeEstoque(p.getQuantidadeEstoque() + item.getQuantidade());
            produtoRepository.save(p);
        });

        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataCancelamento(LocalDateTime.now());
        pedido.setMotivoCancelamento(motivo);

        log.info("Pedido id={} cancelado. Estoque devolvido.", id);
        return pedidoRepository.save(pedido);
    }


    @Override
    @Transactional
    public Pedido editarObservacao(Long id, String observacao, String emailUsuario) {
        Pedido pedido = buscarComPermissao(id, emailUsuario);
        pedido.setObservacao(observacao);
        return pedidoRepository.save(pedido);
    }


    @Override
    @Transactional
    public void removerPedido(Long id, String emailUsuario) {
        Pedido pedido = buscarComPermissao(id, emailUsuario);
        if (pedido.getStatus() != StatusPedido.ENTREGUE && pedido.getStatus() != StatusPedido.CANCELADO)
            throw new ApiException("Finalize ou cancele o pedido antes de removê-lo.", HttpStatus.CONFLICT, PATH);
        pedidoRepository.delete(pedido);
        log.info("Pedido id={} removido do histórico.", id);
    }


    @Override
    @Transactional
    public void limparHistorico(Long empresaId, String emailUsuario) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada", HttpStatus.NOT_FOUND, PATH));

        if (!empresa.getDono().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão.", HttpStatus.FORBIDDEN, PATH);

        List<Pedido> todos = pedidoRepository.findByEmpresaIdOrderByDataPedidoDesc(empresaId);
        if (todos.stream().anyMatch(p -> p.getStatus() != StatusPedido.ENTREGUE
                && p.getStatus() != StatusPedido.CANCELADO))
            throw new ApiException("Existem pedidos ativos no histórico.", HttpStatus.CONFLICT, PATH);
        pedidoRepository.deleteAll(todos);
        log.info("Histórico de pedidos da empresa {} limpo.", empresaId);
    }


    @Override
    @Transactional(readOnly = true)
    public List<Pedido> listarPorEmpresa(Long empresaId, String emailUsuario) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada", HttpStatus.NOT_FOUND, PATH));
        if (!empresa.getDono().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão.", HttpStatus.FORBIDDEN, PATH);
        return pedidoRepository.findByEmpresaIdOrderByDataPedidoDesc(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public Pedido buscarPorId(Long id, String emailUsuario) {
        return buscarComPermissao(id, emailUsuario);
    }

    private Pedido buscarSemValidarPermissao(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Pedido não encontrado", HttpStatus.NOT_FOUND, PATH));
    }


    private Pedido buscarComPermissao(Long id, String emailUsuario) {
        Pedido pedido = buscarSemValidarPermissao(id);
        if (!pedido.getUsuario().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão para este pedido.", HttpStatus.FORBIDDEN, PATH);
        return pedido;
    }

    private boolean transicaoPermitida(StatusPedido atual, StatusPedido novoStatus) {
        return switch (atual) {
            case PENDENTE -> novoStatus == StatusPedido.CONFIRMADO;
            case CONFIRMADO -> novoStatus == StatusPedido.ENVIADO || novoStatus == StatusPedido.ENTREGUE;
            case ENVIADO -> novoStatus == StatusPedido.ENTREGUE;
            case ENTREGUE, CANCELADO -> false;
        };
    }
}
