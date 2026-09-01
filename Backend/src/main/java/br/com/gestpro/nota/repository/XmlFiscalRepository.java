package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.model.XmlFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface XmlFiscalRepository extends JpaRepository<XmlFiscal, Long> {
    Optional<XmlFiscal> findByDocumentoIdAndTipo(Long documentoId, XmlFiscal.Tipo tipo);
}
