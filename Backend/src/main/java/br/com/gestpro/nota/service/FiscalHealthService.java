package br.com.gestpro.nota.service;

import br.com.gestpro.nota.config.FiscalResilienceConfig;
import br.com.gestpro.nota.dto.FiscalHealthResponse;
import br.com.gestpro.nota.model.FiscalJob;
import br.com.gestpro.nota.repository.FiscalJobRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class FiscalHealthService {
    private final CircuitBreakerRegistry registry;
    private final FiscalJobRepository jobs;
    public FiscalHealthResponse obter() {
        var breaker = registry.circuitBreaker(FiscalResilienceConfig.SEFAZ);
        var metrics = breaker.getMetrics();
        return new FiscalHealthResponse("SEFAZ_DIRETO", breaker.getState().name(),
                metrics.getNumberOfFailedCalls(), metrics.getNumberOfSuccessfulCalls(),
                Math.toIntExact(jobs.countByStatus(FiscalJob.Status.PENDENTE)));
    }
}
