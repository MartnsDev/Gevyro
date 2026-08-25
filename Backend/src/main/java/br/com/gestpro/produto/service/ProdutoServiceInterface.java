package br.com.gestpro.produto.service;

import br.com.gestpro.produto.dto.CriarProdutoDTO;
import br.com.gestpro.produto.model.Produto;

import java.util.List;

public interface ProdutoServiceInterface {
    Produto criar(CriarProdutoDTO dto);
    Produto atualizar(Long id, CriarProdutoDTO dto);
    void excluir(Long id, String emailUsuario);
    Produto buscarPorId(Long id, String emailUsuario);
    List<Produto> listarPorEmail(String email);
    List<Produto> listarPorEmpresa(Long empresaId, String emailUsuario);
    List<Produto> listarTodos();
    Produto salvar(Produto produto);
    List<Produto> listarPorUsuario(Long usuarioId);
}
