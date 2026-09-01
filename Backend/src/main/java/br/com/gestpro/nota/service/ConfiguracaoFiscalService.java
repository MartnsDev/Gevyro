package br.com.gestpro.nota.service;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.dto.ConfiguracaoFiscalRequest;
import br.com.gestpro.nota.dto.ConfiguracaoFiscalResponse;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service @RequiredArgsConstructor
public class ConfiguracaoFiscalService {
    private final ConfiguracaoFiscalEmpresaRepository repository;
    private final EmpresaRepository empresaRepository;
    private final FiscalEncryptionService encryptionService;
    private final FiscalAuditService auditService;

    @Transactional(readOnly = true)
    public ConfiguracaoFiscalResponse buscar(Long empresaId, String ator) {
        validarAcesso(empresaId, ator);
        return repository.findByEmpresaId(empresaId).map(this::toResponse)
                .orElse(new ConfiguracaoFiscalResponse(empresaId, null, null,
                        ConfiguracaoFiscalEmpresa.Ambiente.HOMOLOGACAO, "1", "1", null, false, null));
    }

    @Transactional
    public ConfiguracaoFiscalResponse salvar(Long empresaId, ConfiguracaoFiscalRequest request, String ator) {
        validarAcesso(empresaId, ator);
        ConfiguracaoFiscalEmpresa config = repository.findByEmpresaId(empresaId)
                .orElseGet(() -> new ConfiguracaoFiscalEmpresa(empresaId));
        boolean substituirCsc = request.csc() != null;
        byte[] plain = substituirCsc ? request.csc().getBytes(StandardCharsets.UTF_8) : null;
        try {
            FiscalEncryptionService.Encrypted encrypted = plain == null || plain.length == 0
                    ? null : encryptionService.encrypt(plain);
            config.atualizar(normalizar(request.inscricaoEstadual()), request.regimeTributario(), request.ambiente(),
                    request.serieNfe(), request.serieNfce(), normalizar(request.cscId()),
                    encrypted == null ? null : encrypted.cipherText(), encrypted == null ? null : encrypted.nonce(),
                    substituirCsc);
            ConfiguracaoFiscalResponse response = toResponse(repository.save(config));
            auditService.registrar(empresaId, null, "CONFIGURACAO_FISCAL_ALTERADA", ator, "SUCESSO",
                    "ambiente=" + request.ambiente());
            return response;
        } finally {
            if (plain != null) Arrays.fill(plain, (byte) 0);
        }
    }

    private void validarAcesso(Long empresaId, String ator) {
        Empresa empresa = empresaRepository.findByIdWithDono(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada.", HttpStatus.NOT_FOUND, "/api/fiscal/configuracao"));
        if (!empresa.getDono().getEmail().equals(ator))
            throw new ApiException("Sem permissão para a configuração fiscal desta empresa.", HttpStatus.FORBIDDEN, "/api/fiscal/configuracao");
    }
    private ConfiguracaoFiscalResponse toResponse(ConfiguracaoFiscalEmpresa c) {
        return new ConfiguracaoFiscalResponse(c.getEmpresaId(), c.getInscricaoEstadual(), c.getRegimeTributario(),
                c.getAmbiente(), c.getSerieNfe(), c.getSerieNfce(), c.getCscId(), c.getCscCifrado() != null, c.getAtualizadoEm());
    }
    private String normalizar(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
