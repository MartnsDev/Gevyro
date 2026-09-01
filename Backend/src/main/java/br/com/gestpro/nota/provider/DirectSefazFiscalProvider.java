package br.com.gestpro.nota.provider;

import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.service.validacoes.SefazComunicacaoService;
import br.com.gestpro.nota.config.FiscalResilienceConfig;
import io.github.resilience4j.circuitbreaker.*;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class DirectSefazFiscalProvider implements FiscalProvider {
    private final SefazComunicacaoService gateway;
    private final CircuitBreaker circuitBreaker;
    public DirectSefazFiscalProvider(SefazComunicacaoService gateway, CircuitBreakerRegistry registry) {
        this.gateway = gateway;
        this.circuitBreaker = registry.circuitBreaker(FiscalResilienceConfig.SEFAZ);
    }
    public String codigo() { return "SEFAZ_DIRETO"; }
    public Set<TipoNota> documentosSuportados() { return Set.of(TipoNota.NFE, TipoNota.NFCE); }

    public AutorizacaoResultado autorizar(AutorizacaoComando c) {
        var r = circuitBreaker.executeSupplier(() -> gateway.enviarNfe(c.xmlAssinado(), c.uf(),
                c.tipo().getModelo(), c.certificado(), c.senhaCertificado(), c.homologacao()));
        return new AutorizacaoResultado(r.isSucesso(), r.getCodigo(), r.getMensagem(), r.getProtocolo(),
                r.getDataHoraRecebimento(), r.getXmlRetorno());
    }
    public EventoResultado cancelar(CancelamentoComando c) {
        var r = circuitBreaker.executeSupplier(() -> gateway.cancelarNfe(c.chaveAcesso(), c.protocolo(),
                c.justificativa(), c.uf(), c.certificado(), c.senhaCertificado(), c.homologacao()));
        return new EventoResultado(r.isSucesso(), r.getCodigo(), r.getMensagem(), r.getProtocolo(), r.getXmlRetorno());
    }
    public SituacaoResultado consultarSituacao(ConsultaSituacaoComando c) {
        var r = circuitBreaker.executeSupplier(() -> gateway.consultarSituacao(c.chaveAcesso(), c.uf(),
                c.certificado(), c.senhaCertificado(), c.homologacao()));
        SituacaoResultado.Situacao situacao = switch (r.getCodigo() == null ? "" : r.getCodigo()) {
            case "100" -> SituacaoResultado.Situacao.AUTORIZADA;
            case "101", "151", "155" -> SituacaoResultado.Situacao.CANCELADA;
            case "217" -> SituacaoResultado.Situacao.NAO_ENCONTRADA;
            case "110", "301", "302" -> SituacaoResultado.Situacao.REJEITADA;
            default -> SituacaoResultado.Situacao.DESCONHECIDA;
        };
        return new SituacaoResultado(situacao, r.getCodigo(), r.getMensagem(), r.getProtocolo(), r.getXmlRetorno());
    }
}
