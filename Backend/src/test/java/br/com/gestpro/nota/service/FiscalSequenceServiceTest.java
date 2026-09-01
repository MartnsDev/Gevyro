package br.com.gestpro.nota.service;

import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.SequenciaFiscal;
import br.com.gestpro.nota.repository.SequenciaFiscalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({FiscalSequenceService.class, FiscalSequenceInitializer.class})
class FiscalSequenceServiceTest {

    @Autowired FiscalSequenceService service;
    @Autowired SequenciaFiscalRepository repository;
    @Autowired TransactionTemplate transactions;

    @Test
    void reservaNumerosSemReutilizarValor() {
        Long primeiro = transactions.execute(status -> service.reservar(10L, TipoNota.NFE, "1", true));
        Long segundo = transactions.execute(status -> service.reservar(10L, TipoNota.NFE, "1", true));

        assertThat(primeiro).isEqualTo(1L);
        assertThat(segundo).isEqualTo(2L);
        assertThat(repository.findAll()).singleElement()
                .extracting(SequenciaFiscal::getProximoNumero).isEqualTo(3L);
    }

    @Test
    void mantemSequenciasSeparadasPorAmbiente() {
        Long homologacao = transactions.execute(status -> service.reservar(10L, TipoNota.NFE, "1", true));
        Long producao = transactions.execute(status -> service.reservar(10L, TipoNota.NFE, "1", false));

        assertThat(homologacao).isEqualTo(1L);
        assertThat(producao).isEqualTo(1L);
        assertThat(repository.findAll()).hasSize(2);
    }
}
