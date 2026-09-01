package br.com.gestpro.nota.repository;


import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.NotaFiscal;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    List<NotaFiscal> findByEmpresaIdOrderByDataEmissaoDesc(Long empresaId);

    List<NotaFiscal> findByEmpresaIdAndStatusOrderByDataEmissaoDesc(Long empresaId, NotaFiscalStatus status);

    List<NotaFiscal> findByEmpresaIdAndTipoOrderByDataEmissaoDesc(Long empresaId, TipoNota tipo);

    Optional<NotaFiscal> findByChaveAcesso(String chaveAcesso);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotaFiscal n where n.id = :id")
    Optional<NotaFiscal> findByIdForUpdate(@Param("id") Long id);

    // Notas em contingência aguardando transmissão
    List<NotaFiscal> findByEmpresaIdAndEmContingenciaAndStatus(
            Long empresaId, Boolean emContingencia, NotaFiscalStatus status
    );

    // Notas por período para exportação do contador
    @Query("SELECT n FROM NotaFiscal n WHERE n.empresaId = :empresaId " +
            "AND n.dataEmissao >= :inicio AND n.dataEmissao <= :fim " +
            "AND n.status IN ('AUTORIZADA', 'CANCELADA') " +
            "ORDER BY n.numeroNota")
    List<NotaFiscal> findByEmpresaIdAndPeriodo(
            @Param("empresaId") Long empresaId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    // Estatísticas por empresa
    @Query("SELECT COUNT(n), n.status FROM NotaFiscal n WHERE n.empresaId = :empresaId GROUP BY n.status")
    List<Object[]> countByStatus(@Param("empresaId") Long empresaId);

    @Query("SELECT SUM(n.valorTotal) FROM NotaFiscal n WHERE n.empresaId = :empresaId AND n.status = 'AUTORIZADA' AND n.dataEmissao >= :inicio")
    Optional<java.math.BigDecimal> sumValorAutorizadasByPeriodo(
            @Param("empresaId") Long empresaId,
            @Param("inicio") LocalDateTime inicio
    );

    @Query("SELECT SUM(n.valorTotal) FROM NotaFiscal n WHERE n.empresaId = :empresaId AND n.status = :status AND n.dataEmissao BETWEEN :inicio AND :fim")
    BigDecimal sumValorTotalByEmpresaIdAndStatusAndDataEmissaoBetween(
            @Param("empresaId") Long empresaId,
            @Param("status") NotaFiscalStatus status,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("SELECT n FROM NotaFiscal n WHERE " +
            "(:empresaId IS NULL OR n.empresaId = :empresaId) AND " +
            "(:status IS NULL OR n.status = :status) AND " +
            "(:tipo IS NULL OR n.tipo = :tipo) AND " +
            "(:clienteNome IS NULL OR LOWER(n.clienteNome) LIKE LOWER(CONCAT('%', :clienteNome, '%'))) AND " +
            "(:inicio IS NULL OR n.createdAt >= :inicio) AND " +
            "(:fim IS NULL OR n.createdAt <= :fim)")
    Page<NotaFiscal> findWithFilters(
            Long empresaId,
            NotaFiscalStatus status,
            TipoNota tipo,
            String clienteNome,
            LocalDateTime inicio,
            LocalDateTime fim,
            Pageable pageable);

    long countByEmpresaIdAndStatus(Long empresaId, NotaFiscalStatus status);

    boolean existsByEmpresaIdAndTipoAndSerieAndNumeroNotaBetween(
            Long empresaId, TipoNota tipo, String serie, Long numeroInicio, Long numeroFim);


}
