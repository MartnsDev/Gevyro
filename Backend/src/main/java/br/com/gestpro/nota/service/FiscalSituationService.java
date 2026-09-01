package br.com.gestpro.nota.service;

import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.provider.FiscalProvider;
import br.com.gestpro.nota.provider.FiscalProviderRegistry;
import br.com.gestpro.nota.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class FiscalSituationService {
    private final NotaFiscalRepository notas;
    private final EmpresaRepository empresas;
    private final ConfiguracaoFiscalEmpresaRepository configuracoes;
    private final CertificateService certificados;
    private final FiscalProviderRegistry providers;

    public FiscalProvider.SituacaoResultado consultar(Long notaId) {
        var nota = notas.findById(notaId).orElseThrow(() -> new ApiException("Nota fiscal não encontrada.",
                HttpStatus.NOT_FOUND, "/api/nota-fiscal/situacao"));
        if (nota.getChaveAcesso() == null || !nota.getChaveAcesso().matches("[0-9]{44}"))
            throw new ApiException("A nota ainda não possui chave válida para consulta.", HttpStatus.CONFLICT,
                    "/api/nota-fiscal/situacao");
        var empresa = empresas.findById(nota.getEmpresaId()).orElseThrow();
        boolean homologacao = configuracoes.findByEmpresaId(nota.getEmpresaId())
                .map(c -> c.getAmbiente() == ConfiguracaoFiscalEmpresa.Ambiente.HOMOLOGACAO).orElse(true);
        try (CertificateService.Material material = certificados.carregar(nota.getEmpresaId())) {
            return providers.oficialPara(nota.getTipo()).consultarSituacao(new FiscalProvider.ConsultaSituacaoComando(
                    nota.getChaveAcesso(), empresa.getUf(), homologacao, material.arquivo(), material.senha()));
        }
    }
}
