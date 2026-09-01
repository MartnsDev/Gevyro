package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.model.FiscalJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FiscalJobRepository extends JpaRepository<FiscalJob, Long> {
    @Query("select j.id from FiscalJob j where j.status = 'PENDENTE' and j.proximaTentativaEm <= :agora order by j.proximaTentativaEm, j.id")
    List<Long> findProntos(@Param("agora") Instant agora, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from FiscalJob j where j.id = :id")
    Optional<FiscalJob> bloquear(@Param("id") Long id);
    Optional<FiscalJob> findByIdempotenciaId(Long idempotenciaId);
    @Query("select j.id from FiscalJob j where j.status = 'PROCESSANDO' and j.iniciadoEm < :limite")
    List<Long> findTravados(@Param("limite") Instant limite, Pageable pageable);
    long countByStatus(FiscalJob.Status status);
}
