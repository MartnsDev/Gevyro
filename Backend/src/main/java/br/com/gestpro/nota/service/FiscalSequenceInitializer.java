package br.com.gestpro.nota.service;

import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.SequenciaFiscal;
import br.com.gestpro.nota.repository.SequenciaFiscalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class FiscalSequenceInitializer {

    private final SequenciaFiscalRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void criar(Long empresaId, TipoNota tipo, String serie, String ambiente) {
        repository.saveAndFlush(new SequenciaFiscal(empresaId, tipo, serie, ambiente, 1L));
    }
}
