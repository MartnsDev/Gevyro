package br.com.gestpro.nota.service;

import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.SequenciaFiscal;
import br.com.gestpro.nota.repository.SequenciaFiscalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FiscalSequenceService {

    private final SequenciaFiscalRepository repository;
    private final FiscalSequenceInitializer initializer;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long reservar(Long empresaId, TipoNota tipo, String serie, boolean homologacao) {
        String ambiente = homologacao ? "HOMOLOGACAO" : "PRODUCAO";
        SequenciaFiscal sequencia = repository.bloquear(empresaId, tipo, serie, ambiente)
                .orElseGet(() -> criarInicial(empresaId, tipo, serie, ambiente));
        return sequencia.reservar();
    }

    private SequenciaFiscal criarInicial(Long empresaId, TipoNota tipo, String serie, String ambiente) {
        try {
            initializer.criar(empresaId, tipo, serie, ambiente);
        } catch (DataIntegrityViolationException concorrencia) {
            // Outra instância criou a mesma sequência entre a consulta e o insert.
        }
        return repository.bloquear(empresaId, tipo, serie, ambiente)
                .orElseThrow(() -> new IllegalStateException("Não foi possível inicializar a sequência fiscal."));
    }
}
