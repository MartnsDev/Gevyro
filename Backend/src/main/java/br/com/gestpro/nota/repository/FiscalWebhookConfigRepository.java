package br.com.gestpro.nota.repository;
import br.com.gestpro.nota.model.FiscalWebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FiscalWebhookConfigRepository extends JpaRepository<FiscalWebhookConfig, Long> {
    Optional<FiscalWebhookConfig> findByEmpresaId(Long empresaId);
}
