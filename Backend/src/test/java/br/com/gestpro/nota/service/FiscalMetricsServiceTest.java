package br.com.gestpro.nota.service;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.repository.FiscalJobRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FiscalMetricsServiceTest {
    @Test void registraResultadosSemIdentificadoresComoTags() {
        var registry = new SimpleMeterRegistry();
        var service = new FiscalMetricsService(registry, mock(FiscalJobRepository.class), CircuitBreakerRegistry.ofDefaults());

        service.emissaoEnfileirada();
        service.concluirAutorizacao(service.iniciarAutorizacao(), NotaFiscalStatus.AUTORIZADA);
        service.retryAgendado();

        assertThat(registry.get("gevyro.fiscal.emissoes.enfileiradas").counter().count()).isEqualTo(1);
        assertThat(registry.get("gevyro.fiscal.emissoes.resultado").tag("resultado", "autorizada").counter().count()).isEqualTo(1);
        assertThat(registry.get("gevyro.fiscal.retries").counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> meter.getId().getTags().forEach(tag ->
                assertThat(tag.getKey()).isEqualTo("resultado")));
    }
}
