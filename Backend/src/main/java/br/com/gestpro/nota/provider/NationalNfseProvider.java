package br.com.gestpro.nota.provider;

import br.com.gestpro.infra.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.Set;

/** Adapter do Emissor Público Nacional. O transporte permanece bloqueado até DPS/XSD estarem implementados. */
@Component
@RequiredArgsConstructor
public class NationalNfseProvider implements NfseProvider {
    static final URI HOMOLOGACAO = URI.create("https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional");
    static final URI PRODUCAO = URI.create("https://sefin.nfse.gov.br/SefinNacional");
    private final NfseNationalHttpClient http;

    @Override public String codigo() { return "SEFIN_NACIONAL"; }
    @Override public boolean nacional() { return true; }
    @Override public Set<Capacidade> capacidades() {
        return Set.of(Capacidade.EMISSAO_DPS, Capacidade.CONSULTA_CHAVE, Capacidade.EVENTOS,
                Capacidade.PARAMETROS_MUNICIPAIS, Capacidade.DANFSE);
    }

    @Override public boolean atendeMunicipio(String codigoIbge) {
        return codigoIbge != null && codigoIbge.matches("[0-9]{7}");
    }

    URI endpoint(boolean homologacao) { return homologacao ? HOMOLOGACAO : PRODUCAO; }

    @Override public EmissaoResultado emitir(EmissaoComando comando) {
        if (comando == null || !atendeMunicipio(comando.codigoMunicipio()))
            throw new IllegalArgumentException("Comando de emissão NFS-e inválido.");
        NfseNationalHttpClient.Resposta resposta = http.emitir(endpoint(comando.homologacao()), comando.homologacao(),
                comando.dpsXmlAssinada(), comando.certificado(), comando.senhaCertificado());
        boolean emitida = resposta.httpStatus() >= 200 && resposta.httpStatus() < 300
                && resposta.nfseXml() != null && resposta.chaveAcesso() != null;
        return new EmissaoResultado(emitida, resposta.chaveAcesso(), resposta.nfseXml(),
                resposta.codigo() == null ? Integer.toString(resposta.httpStatus()) : resposta.codigo(), resposta.motivo());
    }
    @Override public ConsultaResultado consultarPorChave(ConsultaComando comando) { throw indisponivel(); }
    @Override public EventoResultado registrarEvento(EventoComando comando) { throw indisponivel(); }

    private ApiException indisponivel() {
        return new ApiException("NFS-e Nacional aguardando geração e validação oficial da DPS v1.01.",
                HttpStatus.NOT_IMPLEMENTED, "/api/nota-fiscal/nfse");
    }
}
