package br.com.gestpro.nota.repository;
import br.com.gestpro.nota.model.FiscalDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FiscalDeliveryRepository extends JpaRepository<FiscalDelivery, Long> {
    Optional<FiscalDelivery> findByDedupKey(String dedupKey);
}
