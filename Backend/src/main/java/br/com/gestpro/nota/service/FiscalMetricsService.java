package br.com.gestpro.nota.service;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.model.FiscalJob;
import br.com.gestpro.nota.repository.FiscalJobRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/** Métricas agregadas e de baixa cardinalidade; identificadores e dados pessoais nunca viram tags. */
@Service
public class FiscalMetricsService {
    private final MeterRegistry registry;
    private final Timer autorizacao;
    private final Counter enfileiradas;
    private final Counter retries;
    private final Counter recuperacoes;
    private final AtomicInteger circuitoAberto = new AtomicInteger();

    public FiscalMetricsService(MeterRegistry registry, FiscalJobRepository jobs,
                                CircuitBreakerRegistry circuitBreakers) {
        this.registry = registry;
        this.autorizacao = Timer.builder("gevyro.fiscal.autorizacao.duracao")
                .description("Tempo do processamento de uma tentativa de autorização fiscal")
                .publishPercentileHistogram().register(registry);
        this.enfileiradas = Counter.builder("gevyro.fiscal.emissoes.enfileiradas")
                .description("Emissões fiscais aceitas pela fila persistente").register(registry);
        this.retries = Counter.builder("gevyro.fiscal.retries")
                .description("Consultas de situação agendadas após resultado desconhecido").register(registry);
        this.recuperacoes = Counter.builder("gevyro.fiscal.jobs.recuperados")
                .description("Jobs fiscais recuperados após interrupção").register(registry);
        Gauge.builder("gevyro.fiscal.fila.pendente", jobs,
                        repo -> repo.countByStatus(FiscalJob.Status.PENDENTE))
                .description("Jobs fiscais pendentes na fila persistente").register(registry);
        var breaker = circuitBreakers.circuitBreaker(br.com.gestpro.nota.config.FiscalResilienceConfig.SEFAZ);
        circuitoAberto.set(breaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED ? 0 : 1);
        breaker.getEventPublisher().onStateTransition(evento ->
                circuitoAberto.set(evento.getStateTransition().getToState()
                        == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED ? 0 : 1));
        Gauge.builder("gevyro.fiscal.circuit_breaker.indisponivel", circuitoAberto, AtomicInteger::get)
                .description("1 quando o circuito fiscal externo não está fechado").register(registry);
    }

    public void emissaoEnfileirada() { enfileiradas.increment(); }
    public Timer.Sample iniciarAutorizacao() { return Timer.start(registry); }

    public void concluirAutorizacao(Timer.Sample inicio, NotaFiscalStatus status) {
        inicio.stop(autorizacao);
        Counter.builder("gevyro.fiscal.emissoes.resultado")
                .tag("resultado", resultadoSeguro(status)).register(registry).increment();
    }

    public void falhaTecnica(Timer.Sample inicio) {
        inicio.stop(autorizacao);
        Counter.builder("gevyro.fiscal.emissoes.resultado")
                .tag("resultado", "erro_tecnico").register(registry).increment();
    }

    public void retryAgendado() { retries.increment(); }
    public void jobRecuperado() { recuperacoes.increment(); }

    private String resultadoSeguro(NotaFiscalStatus status) {
        return switch (status) {
            case AUTORIZADA -> "autorizada";
            case REJEITADA -> "rejeitada";
            case CANCELADA -> "cancelada";
            default -> "outro";
        };
    }
}
