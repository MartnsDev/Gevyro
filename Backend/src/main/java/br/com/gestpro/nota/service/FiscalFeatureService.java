package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.TipoNota;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class FiscalFeatureService {
    private final ConfiguracaoFiscalEmpresaRepository repository;

    @Transactional(readOnly = true)
    public void validarEmissaoHabilitada(Long empresaId, TipoNota tipo) {
        ConfiguracaoFiscalEmpresa config = repository.findByEmpresaId(empresaId)
                .orElseThrow(() -> bloqueado("Configure e habilite o módulo fiscal antes da emissão."));
        boolean documentoHabilitado = switch (tipo) {
            case NFE -> config.isNfeHabilitada();
            case NFCE -> config.isNfceHabilitada();
            case NFSE -> config.isNfseHabilitada();
        };
        if (!config.isFiscalHabilitado() || !documentoHabilitado)
            throw bloqueado(tipo.getDescricao() + " não está habilitada para esta empresa.");
    }

    private ApiException bloqueado(String mensagem) {
        return new ApiException(mensagem, HttpStatus.PRECONDITION_FAILED, "/api/nota-fiscal/emitir");
    }
}
