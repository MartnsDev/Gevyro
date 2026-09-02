package br.com.gestpro.produto.repository;

import br.com.gestpro.produto.model.ProdutoConfiguracaoFiscal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface ProdutoConfiguracaoFiscalRepository extends JpaRepository<ProdutoConfiguracaoFiscal, Long> {
    Optional<ProdutoConfiguracaoFiscal> findTopByProdutoIdOrderByVersaoDesc(Long produtoId);
    List<ProdutoConfiguracaoFiscal> findByProdutoIdOrderByVersaoDesc(Long produtoId);
    @Query("select c from ProdutoConfiguracaoFiscal c where c.produtoId=:produtoId and c.vigenciaInicio<=:data "
            + "and (c.vigenciaFim is null or c.vigenciaFim>=:data) order by c.versao desc")
    List<ProdutoConfiguracaoFiscal> vigentes(@Param("produtoId") Long produtoId, @Param("data") LocalDate data);
}
