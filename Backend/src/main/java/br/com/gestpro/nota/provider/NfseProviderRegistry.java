package br.com.gestpro.nota.provider;

import br.com.gestpro.infra.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NfseProviderRegistry {
    private final List<NfseProvider> providers;

    public NfseProviderRegistry(List<NfseProvider> providers) { this.providers = List.copyOf(providers); }

    public NfseProvider paraMunicipio(String codigoIbge) {
        if (codigoIbge == null || !codigoIbge.matches("[0-9]{7}"))
            throw new ApiException("Código IBGE municipal inválido.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/nfse");
        return providers.stream().filter(NfseProvider::nacional).filter(p -> p.atendeMunicipio(codigoIbge))
                .findFirst().orElseGet(() -> providers.stream().filter(p -> p.atendeMunicipio(codigoIbge))
                        .findFirst().orElseThrow(() -> new ApiException(
                                "Município sem adapter NFS-e configurado.", HttpStatus.NOT_IMPLEMENTED,
                                "/api/nota-fiscal/nfse")));
    }
}
