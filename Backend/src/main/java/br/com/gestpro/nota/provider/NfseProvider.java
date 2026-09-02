package br.com.gestpro.nota.provider;

import java.util.Set;

/** Contrato próprio da NFS-e; DPS e eventos não compartilham o domínio da NF-e/NFC-e. */
public interface NfseProvider {
    String codigo();
    boolean nacional();
    Set<Capacidade> capacidades();
    boolean atendeMunicipio(String codigoIbge);

    EmissaoResultado emitir(EmissaoComando comando);
    ConsultaResultado consultarPorChave(ConsultaComando comando);
    EventoResultado registrarEvento(EventoComando comando);

    enum Capacidade { EMISSAO_DPS, CONSULTA_CHAVE, EVENTOS, PARAMETROS_MUNICIPAIS, DANFSE }

    record EmissaoComando(String dpsXmlAssinada, String codigoMunicipio, boolean homologacao,
                          byte[] certificado, String senhaCertificado) {}
    record ConsultaComando(String chaveAcesso, boolean homologacao,
                           byte[] certificado, String senhaCertificado) {}
    record EventoComando(String pedidoEventoXmlAssinado, String chaveAcesso, boolean homologacao,
                         byte[] certificado, String senhaCertificado) {}
    record EmissaoResultado(boolean emitida, String chaveAcesso, String nfseXml, String codigo, String motivo) {}
    record ConsultaResultado(boolean encontrada, String nfseXml, String codigo, String motivo) {}
    record EventoResultado(boolean aceito, String eventoXml, String codigo, String motivo) {}
}
