package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.SequenciaFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SequenciaFiscalRepository extends JpaRepository<SequenciaFiscal, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SequenciaFiscal s where s.empresaId = :empresaId and s.tipo = :tipo " +
            "and s.serie = :serie and s.ambiente = :ambiente")
    Optional<SequenciaFiscal> bloquear(@Param("empresaId") Long empresaId,
                                      @Param("tipo") TipoNota tipo,
                                      @Param("serie") String serie,
                                      @Param("ambiente") String ambiente);
}
