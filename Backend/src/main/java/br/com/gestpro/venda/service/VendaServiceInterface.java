package br.com.gestpro.venda.service;

import br.com.gestpro.venda.dto.RegistrarVendaDTO;
import br.com.gestpro.venda.model.Venda;

import java.util.List;

public interface VendaServiceInterface {
    Venda registrarVenda(RegistrarVendaDTO dto);
    List<Venda> listarPorCaixa(Long idCaixa, String emailUsuario);
    Venda buscarPorId(Long id, String emailUsuario);
    Venda cancelarVenda(Long id, String motivo, String emailUsuario);
    Venda editarObservacao(Long id, String observacao, String emailUsuario);
}
