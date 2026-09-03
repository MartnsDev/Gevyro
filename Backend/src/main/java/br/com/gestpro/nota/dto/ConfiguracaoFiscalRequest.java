package br.com.gestpro.nota.dto;

import br.com.gestpro.nota.RegimeTributario;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

public record ConfiguracaoFiscalRequest(
        @Size(max = 20) String inscricaoEstadual,
        @NotNull RegimeTributario regimeTributario,
        @NotNull ConfiguracaoFiscalEmpresa.Ambiente ambiente,
        @NotBlank @Pattern(regexp = "[0-9]{1,3}") String serieNfe,
        @NotBlank @Pattern(regexp = "[0-9]{1,3}") String serieNfce,
        @Size(max = 20) String cscId,
        @Size(max = 200) String csc,
        boolean fiscalHabilitado,
        boolean nfeHabilitada,
        boolean nfceHabilitada,
        boolean nfseHabilitada,
        boolean confirmarProducao,
        @Size(max = 20) String inscricaoMunicipal,
        @Pattern(regexp = "^$|[0-9]{7}") String cnae,
        @Pattern(regexp = "^$|[0-9]{7}") String codigoIbge,
        @Size(max = 60) String complemento,
        @Email @Size(max = 254) String emailFiscal
) {}
