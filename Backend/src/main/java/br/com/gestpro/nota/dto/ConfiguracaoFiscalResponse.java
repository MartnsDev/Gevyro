package br.com.gestpro.nota.dto;

import br.com.gestpro.nota.RegimeTributario;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import java.time.Instant;

public record ConfiguracaoFiscalResponse(
        Long empresaId, String inscricaoEstadual, RegimeTributario regimeTributario,
        ConfiguracaoFiscalEmpresa.Ambiente ambiente, String serieNfe, String serieNfce,
        String cscId, boolean cscConfigurado, boolean fiscalHabilitado,
        boolean nfeHabilitada, boolean nfceHabilitada, boolean nfseHabilitada, Instant atualizadoEm
) {}
