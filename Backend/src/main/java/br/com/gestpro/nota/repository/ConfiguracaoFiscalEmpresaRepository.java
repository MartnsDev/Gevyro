package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConfiguracaoFiscalEmpresaRepository extends JpaRepository<ConfiguracaoFiscalEmpresa, Long> {
    Optional<ConfiguracaoFiscalEmpresa> findByEmpresaId(Long empresaId);
}
