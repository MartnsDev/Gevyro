package br.com.gestpro.nota.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;

/** Dados mínimos explícitos para uma DPS nacional; não infere tributação municipal. */
public record DpsNacionalDados(
        boolean homologacao,
        OffsetDateTime emissao,
        LocalDate competencia,
        String serie,
        long numero,
        String codigoMunicipioEmissor,
        String cnpjPrestador,
        String inscricaoMunicipal,
        int opcaoSimplesNacional,
        int regimeEspecialTributacao,
        String codigoMunicipioPrestacao,
        String codigoTributacaoNacional,
        String descricaoServico,
        BigDecimal valorServico,
        int tributacaoIssqn,
        int retencaoIssqn,
        BigDecimal aliquotaIssqn
) {}
