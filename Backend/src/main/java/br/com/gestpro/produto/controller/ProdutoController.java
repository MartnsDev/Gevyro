package br.com.gestpro.produto.controller;

import br.com.gestpro.produto.dto.CriarProdutoDTO;
import br.com.gestpro.produto.dto.ProdutoResponseDTO;
import br.com.gestpro.produto.model.Produto;
import br.com.gestpro.produto.service.ProdutoServiceInterface;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {

    private final ProdutoServiceInterface produtoService;

    public ProdutoController(ProdutoServiceInterface produtoService) {
        this.produtoService = produtoService;
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> criar(
            @Valid @RequestBody CriarProdutoDTO dto,
            Authentication authentication) {
        dto.setEmailUsuario(authentication.getName());
        Produto produto = produtoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProdutoResponseDTO(produto));
    }

    /** Lista produtos de uma empresa específica */
    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listar(
            @RequestParam(required = false) Long empresaId,
            Authentication authentication) {
        List<Produto> produtos;
        if (empresaId != null) {
            produtos = produtoService.listarPorEmpresa(empresaId, authentication.getName());
        } else {
            produtos = produtoService.listarPorEmail(authentication.getName());
        }
        return ResponseEntity.ok(produtos.stream().map(ProdutoResponseDTO::new).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(new ProdutoResponseDTO(produtoService.buscarPorId(id, authentication.getName())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody CriarProdutoDTO dto,
            Authentication authentication) {
        dto.setEmailUsuario(authentication.getName());
        return ResponseEntity.ok(new ProdutoResponseDTO(produtoService.atualizar(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @PathVariable Long id,
            Authentication authentication) {
        produtoService.excluir(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
