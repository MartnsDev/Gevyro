package br.com.gestpro.nota.service;

import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.provider.*;
import br.com.gestpro.nota.service.validacoes.SefazComunicacaoService;
import io.github.resilience4j.circuitbreaker.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectSefazFiscalProviderTest {
    @Test
    void abreCircuitoDepoisDeFalhasDeTransporte() {
        SefazComunicacaoService gateway = mock(SefazComunicacaoService.class);
        when(gateway.enviarNfe(anyString(), anyString(), anyString(), any(), anyString(), anyBoolean()))
                .thenThrow(new FiscalProviderException("timeout", true, null));
        CircuitBreakerConfig config = CircuitBreakerConfig.custom().slidingWindowSize(2).minimumNumberOfCalls(2)
                .failureRateThreshold(50).waitDurationInOpenState(Duration.ofMinutes(1)).build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        DirectSefazFiscalProvider provider = new DirectSefazFiscalProvider(gateway, registry);
        var comando = new FiscalProvider.AutorizacaoComando("<xml/>", "SP", TipoNota.NFE, true, new byte[]{1}, "senha");

        assertThatThrownBy(() -> provider.autorizar(comando)).isInstanceOf(FiscalProviderException.class);
        assertThatThrownBy(() -> provider.autorizar(comando)).isInstanceOf(FiscalProviderException.class);
        assertThat(registry.circuitBreaker("sefazDireto").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> provider.autorizar(comando)).isInstanceOf(CallNotPermittedException.class);
        verify(gateway, times(2)).enviarNfe(anyString(), anyString(), anyString(), any(), anyString(), anyBoolean());
    }
}
