package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.model.FiscalIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FiscalIdempotencyRepository extends JpaRepository<FiscalIdempotency, Long> {
    Optional<FiscalIdempotency> findByEmpresaIdAndOperacaoAndIdempotencyKey(Long empresaId, String operacao, String key);
}
