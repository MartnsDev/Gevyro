package br.com.gestpro.nota.config;

import io.github.resilience4j.circuitbreaker.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class FiscalResilienceConfig {
    public static final String SEFAZ = "sefazDireto";

    @Bean
    CircuitBreakerRegistry fiscalCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(70)
                .slowCallDurationThreshold(Duration.ofSeconds(20))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(2)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .recordException(error -> error instanceof br.com.gestpro.nota.provider.FiscalProviderException)
                .build();
        return CircuitBreakerRegistry.of(config);
    }
}
