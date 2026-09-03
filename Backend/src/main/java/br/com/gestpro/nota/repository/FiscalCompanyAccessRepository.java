package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.model.FiscalCompanyAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface FiscalCompanyAccessRepository extends JpaRepository<FiscalCompanyAccess, Long> {
    @Query("select a from FiscalCompanyAccess a join Usuario u on u.id = a.usuarioId where a.empresaId = :empresaId and lower(u.email) = lower(:email) and a.ativo = true")
    Optional<FiscalCompanyAccess> findActive(@Param("empresaId") Long empresaId, @Param("email") String email);
    Optional<FiscalCompanyAccess> findByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);
    Optional<FiscalCompanyAccess> findByIdAndEmpresaId(Long id, Long empresaId);
    List<FiscalCompanyAccess> findByEmpresaIdOrderByIdAsc(Long empresaId);
    long countByEmpresaIdAndAtivoTrue(Long empresaId);
}
