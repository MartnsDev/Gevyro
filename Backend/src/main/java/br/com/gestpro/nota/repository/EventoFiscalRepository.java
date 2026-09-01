package br.com.gestpro.nota.repository;

import br.com.gestpro.nota.model.EventoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoFiscalRepository extends JpaRepository<EventoFiscal, Long> {
    @Query("select coalesce(max(e.sequencia), 0) from EventoFiscal e where e.documentoId=:documentoId and e.tipo=:tipo")
    int maiorSequencia(@Param("documentoId") Long documentoId, @Param("tipo") EventoFiscal.Tipo tipo);
}
