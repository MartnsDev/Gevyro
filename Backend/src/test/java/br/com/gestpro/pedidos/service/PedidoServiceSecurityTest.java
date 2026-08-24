package br.com.gestpro.pedidos.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.cliente.repository.ClienteRepository;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.pedidos.Repository.PedidoRepository;
import br.com.gestpro.pedidos.dto.StatusPedido;
import br.com.gestpro.pedidos.model.Pedido;
import br.com.gestpro.produto.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceSecurityTest {
    @Mock PedidoRepository pedidoRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock EmpresaRepository empresaRepository;
    @Mock ClienteRepository clienteRepository;
    @InjectMocks PedidoServiceImpl service;

    @Test
    void deveBloquearBuscaDePedidoDeOutroUsuario() {
        Pedido pedido = pedidoDoUsuario("dono@teste.com", StatusPedido.CONFIRMADO);
        when(pedidoRepository.findById(30L)).thenReturn(Optional.of(pedido));

        assertThrows(ApiException.class,
                () -> service.buscarPorId(30L, "intruso@teste.com"));
    }

    @Test
    void deveBloquearRetrocessoDeStatus() {
        Pedido pedido = pedidoDoUsuario("dono@teste.com", StatusPedido.CONFIRMADO);
        when(pedidoRepository.findById(30L)).thenReturn(Optional.of(pedido));

        assertThrows(ApiException.class,
                () -> service.atualizarStatus(30L, StatusPedido.PENDENTE, "dono@teste.com"));
    }

    private Pedido pedidoDoUsuario(String email, StatusPedido status) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setStatus(status);
        return pedido;
    }
}
