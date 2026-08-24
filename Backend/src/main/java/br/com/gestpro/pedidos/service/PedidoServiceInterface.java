package br.com.gestpro.pedidos.service;


import br.com.gestpro.pedidos.dto.RegistrarPedidoDTO;
import br.com.gestpro.pedidos.dto.StatusPedido;
import br.com.gestpro.pedidos.model.Pedido;

import java.util.List;

public interface PedidoServiceInterface {

    /** Cria um novo pedido e debita o estoque imediatamente */
    Pedido registrarPedido(RegistrarPedidoDTO dto);

    /** Avança ou retrocede o status do pedido */
    Pedido atualizarStatus(Long id, StatusPedido novoStatus, String emailUsuario);

    /** Cancela o pedido e devolve o estoque */
    Pedido cancelarPedido(Long id, String motivo, String emailUsuario);

    /** Edita somente a observação */
    Pedido editarObservacao(Long id, String observacao, String emailUsuario);

    /** Lista todos os pedidos de uma empresa */
    List<Pedido> listarPorEmpresa(Long empresaId, String emailUsuario);

    /** Busca um pedido pelo ID */
    Pedido buscarPorId(Long id, String emailUsuario);

    /**
     * Remove um único pedido do histórico (exclusão física).
     * Se o pedido não estiver cancelado, o estoque NÃO é devolvido —
     * o operador deve cancelar primeiro se quiser devolver o estoque.
     */
    void removerPedido(Long id, String emailUsuario);

    /**
     * Remove TODO o histórico de pedidos de uma empresa (exclusão física).
     * Usar com cuidado — operação irreversível.
     */
    void limparHistorico(Long empresaId, String emailUsuario);
}
