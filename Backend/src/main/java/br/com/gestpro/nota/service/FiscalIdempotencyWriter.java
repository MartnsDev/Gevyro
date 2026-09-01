package br.com.gestpro.nota.service;

import br.com.gestpro.nota.model.FiscalIdempotency;
import br.com.gestpro.nota.repository.FiscalIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
class FiscalIdempotencyWriter {
    private final FiscalIdempotencyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void iniciar(Long empresaId, Long documentoId, String operacao, String key) {
        repository.saveAndFlush(new FiscalIdempotency(empresaId, documentoId, operacao, key));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluir(Long id) { repository.findById(id).orElseThrow().concluir(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(Long id) { repository.findById(id).orElseThrow().falhar(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resultadoDesconhecido(Long id) { repository.findById(id).orElseThrow().resultadoDesconhecido(); }
}
