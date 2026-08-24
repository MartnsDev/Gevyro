package br.com.gestpro.produto.repository;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.produto.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE p.id = :id")
    Optional<Produto> findByIdForUpdate(@Param("id") Long id);

    // Consulta mantida para integrações antigas.
    List<Produto> findByUsuario(Usuario usuario);

    // Consulta principal por empresa.
    List<Produto> findByEmpresa(Empresa empresa);

    // Produtos zerados por empresa
    List<Produto> findByQuantidadeEstoqueAndEmpresaId(int quantidade, Long empresaId);

    // Usada nos alertas de estoque.
    List<Produto> findByQuantidadeEstoqueAndUsuarioEmail(int quantidade, String email);

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.empresa = :empresa AND p.ativo = true")
    long countByEmpresaAndAtivoTrue(@Param("empresa") Empresa empresa);

    @Query("SELECT COUNT(iv) > 0 FROM ItemVenda iv WHERE iv.produto.id = :produtoId")
    boolean isProdutoVinculadoAVenda(@Param("produtoId") Long produtoId);
}
