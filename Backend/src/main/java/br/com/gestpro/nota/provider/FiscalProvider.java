package br.com.gestpro.nota.provider;

import br.com.gestpro.nota.TipoNota;
import java.util.Set;

public interface FiscalProvider {
    String codigo();
    Set<TipoNota> documentosSuportados();
    AutorizacaoResultado autorizar(AutorizacaoComando comando);
    EventoResultado cancelar(CancelamentoComando comando);
    SituacaoResultado consultarSituacao(ConsultaSituacaoComando comando);
    EventoResultado inutilizar(InutilizacaoComando comando);

    record AutorizacaoComando(String xmlAssinado, String uf, TipoNota tipo, boolean homologacao,
                              byte[] certificado, String senhaCertificado) {}
    record CancelamentoComando(String chaveAcesso, String protocolo, String justificativa, String uf,
                               boolean homologacao, byte[] certificado, String senhaCertificado) {}
    record ConsultaSituacaoComando(String chaveAcesso, String uf, boolean homologacao,
                                   byte[] certificado, String senhaCertificado) {}
    record InutilizacaoComando(String cnpj, String uf, TipoNota tipo, int ano, int serie,
                               long numeroInicio, long numeroFim, String justificativa,
                               boolean homologacao, byte[] certificado, String senhaCertificado) {}
    record AutorizacaoResultado(boolean autorizada, String codigo, String motivo, String protocolo,
                                String dataRecebimento, String xmlRetorno) {}
    record EventoResultado(boolean aceito, String codigo, String motivo, String protocolo, String xmlRetorno) {}
    record SituacaoResultado(Situacao situacao, String codigo, String motivo, String protocolo, String xmlRetorno) {
        public enum Situacao { AUTORIZADA, CANCELADA, REJEITADA, NAO_ENCONTRADA, DESCONHECIDA }
    }
}
