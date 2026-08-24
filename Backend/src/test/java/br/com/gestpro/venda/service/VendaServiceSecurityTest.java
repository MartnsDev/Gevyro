package br.com.gestpro.venda.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.caixa.model.Caixa;
import br.com.gestpro.caixa.repository.CaixaRepository;
import br.com.gestpro.cliente.repository.ClienteRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.produto.repository.ProdutoRepository;
import br.com.gestpro.venda.model.Venda;
import br.com.gestpro.venda.repository.VendaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendaServiceSecurityTest {
    @Mock VendaRepository vendaRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock CaixaRepository caixaRepository;
    @Mock ClienteRepository clienteRepository;
    @InjectMocks VendaServiceImpl service;

    @Test
    void deveBloquearListagemDeCaixaDeOutroUsuario() {
        Usuario dono = new Usuario();
        dono.setEmail("dono@teste.com");
        Caixa caixa = new Caixa();
        caixa.setUsuario(dono);
        when(caixaRepository.findById(10L)).thenReturn(Optional.of(caixa));

        assertThrows(ApiException.class,
                () -> service.listarPorCaixa(10L, "intruso@teste.com"));
    }

    @Test
    void deveBloquearBuscaDeVendaDeOutroUsuario() {
        Usuario dono = new Usuario();
        dono.setEmail("dono@teste.com");
        Venda venda = new Venda();
        venda.setUsuario(dono);
        when(vendaRepository.findById(20L)).thenReturn(Optional.of(venda));

        assertThrows(ApiException.class,
                () -> service.buscarPorId(20L, "intruso@teste.com"));
    }
}
