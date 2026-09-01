package br.com.gestpro.nota.repository;
import br.com.gestpro.nota.model.CertificadoDigital;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CertificadoDigitalRepository extends JpaRepository<CertificadoDigital, Long> {
    Optional<CertificadoDigital> findByEmpresaId(Long empresaId);
}
