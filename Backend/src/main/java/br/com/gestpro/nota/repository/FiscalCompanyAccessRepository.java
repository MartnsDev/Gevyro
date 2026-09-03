package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.model.FiscalCompanyAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface FiscalCompanyAccessRepository extends JpaRepository<FiscalCompanyAccess, Long> {
    @Query("select a from FiscalCompanyAccess a join Usuario u on u.id = a.usuarioId where a.empresaId = :empresaId and lower(u.email) = lower(:email) and a.ativo = true")
    Optional<FiscalCompanyAccess> findActive(@Param("empresaId") Long empresaId, @Param("email") String email);
}
