package br.com.gestpro.nota.provider;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.TipoNota;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class FiscalProviderRegistry {
    private final List<FiscalProvider> providers;
    public FiscalProviderRegistry(List<FiscalProvider> providers) { this.providers = List.copyOf(providers); }
    public FiscalProvider oficialPara(TipoNota tipo) {
        return providers.stream().filter(p -> "SEFAZ_DIRETO".equals(p.codigo()) && p.documentosSuportados().contains(tipo))
                .findFirst().orElseThrow(() -> new ApiException("Não há provedor oficial configurado para " + tipo + ".",
                        HttpStatus.NOT_IMPLEMENTED, "/api/nota-fiscal"));
    }
}
