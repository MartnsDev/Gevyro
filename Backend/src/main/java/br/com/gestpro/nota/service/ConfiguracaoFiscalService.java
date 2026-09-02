package br.com.gestpro.nota.service;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.dto.ConfiguracaoFiscalRequest;
import br.com.gestpro.nota.dto.ConfiguracaoFiscalResponse;
import br.com.gestpro.nota.dto.ProntidaoFiscalResponse;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.model.CertificadoDigital;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import br.com.gestpro.nota.repository.CertificadoDigitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class ConfiguracaoFiscalService {
    private final ConfiguracaoFiscalEmpresaRepository repository;
    private final EmpresaRepository empresaRepository;
    private final FiscalEncryptionService encryptionService;
    private final FiscalAuditService auditService;
    private final CertificadoDigitalRepository certificadoRepository;

    @Transactional(readOnly = true)
    public ConfiguracaoFiscalResponse buscar(Long empresaId, String ator) {
        validarAcesso(empresaId, ator);
        return repository.findByEmpresaId(empresaId).map(this::toResponse)
                .orElse(new ConfiguracaoFiscalResponse(empresaId, null, null,
                        ConfiguracaoFiscalEmpresa.Ambiente.HOMOLOGACAO, "1", "1", null, false,
                        false, false, false, false, null));
    }

    @Transactional(readOnly = true)
    public ProntidaoFiscalResponse prontidao(Long empresaId, String ator) {
        Empresa empresa = validarAcesso(empresaId, ator);
        ConfiguracaoFiscalEmpresa config = repository.findByEmpresaId(empresaId).orElse(null);
        CertificadoDigital certificado = certificadoRepository.findByEmpresaId(empresaId).orElse(null);

        boolean documento = somenteDigitos(empresa.getCnpj()).length() == 14;
        boolean identificacao = preenchido(empresa.getRazaoSocial()) && preenchido(empresa.getNomeFantasia());
        boolean endereco = preenchido(empresa.getCep()) && preenchido(empresa.getLogradouro())
                && preenchido(empresa.getNumero()) && preenchido(empresa.getBairro())
                && preenchido(empresa.getCidade()) && empresa.getUf() != null && empresa.getUf().trim().length() == 2;
        boolean tributacao = config != null && config.getRegimeTributario() != null && preenchido(config.getInscricaoEstadual());
        boolean series = config != null && serieValida(config.getSerieNfe()) && serieValida(config.getSerieNfce());
        boolean certificadoValido = certificado != null && certificado.getValidoDe() != null && certificado.getValidoAte() != null
                && !certificado.getValidoDe().isAfter(Instant.now()) && certificado.getValidoAte().isAfter(Instant.now());
        boolean csc = config != null && preenchido(config.getCscId()) && config.getCscCifrado() != null;

        List<ProntidaoFiscalResponse.Requisito> requisitos = List.of(
                requisito("CNPJ", "CNPJ com 14 dígitos cadastrado", documento),
                requisito("IDENTIFICACAO", "Razão social e nome fantasia", identificacao),
                requisito("ENDERECO", "Endereço fiscal completo", endereco),
                requisito("TRIBUTACAO", "Inscrição estadual e regime tributário", tributacao),
                requisito("SERIES", "Séries de NF-e e NFC-e", series),
                requisito("CERTIFICADO", "Certificado A1 vigente", certificadoValido),
                requisito("CSC", "Identificador e segredo CSC da NFC-e", csc));
        int concluidos = (int) requisitos.stream().filter(ProntidaoFiscalResponse.Requisito::concluido).count();
        int percentual = concluidos * 100 / requisitos.size();
        boolean baseNfe = documento && identificacao && endereco && tributacao && series && certificadoValido;
        boolean modulo = config != null && config.isFiscalHabilitado();
        boolean nfePronta = modulo && config.isNfeHabilitada() && baseNfe;
        boolean nfcePronta = modulo && config.isNfceHabilitada() && baseNfe && csc;
        List<String> alertas = new ArrayList<>();
        if (!modulo) alertas.add("O módulo fiscal está desabilitado para esta empresa.");
        if (certificado != null && certificado.getValidoAte() != null
                && certificado.getValidoAte().isAfter(Instant.now())
                && certificado.getValidoAte().isBefore(Instant.now().plusSeconds(30L * 24 * 60 * 60)))
            alertas.add("O certificado A1 vence em menos de 30 dias.");
        alertas.add("A prontidão cadastral não substitui homologação nem validação do responsável fiscal.");
        alertas.add("A transmissão de NFS-e permanece bloqueada nesta versão.");
        return new ProntidaoFiscalResponse(empresaId, percentual, requisitos, nfePronta, nfcePronta, false,
                List.copyOf(alertas));
    }

    @Transactional
    public ConfiguracaoFiscalResponse salvar(Long empresaId, ConfiguracaoFiscalRequest request, String ator) {
        validarAcesso(empresaId, ator);
        ConfiguracaoFiscalEmpresa config = repository.findByEmpresaId(empresaId)
                .orElseGet(() -> new ConfiguracaoFiscalEmpresa(empresaId));
        validarFlags(request);
        boolean entrandoEmProducao = request.ambiente() == ConfiguracaoFiscalEmpresa.Ambiente.PRODUCAO
                && config.getAmbiente() != ConfiguracaoFiscalEmpresa.Ambiente.PRODUCAO;
        if (entrandoEmProducao && !request.confirmarProducao())
            throw new ApiException("A mudança para produção exige confirmação explícita.", HttpStatus.PRECONDITION_REQUIRED,
                    "/api/fiscal/configuracao");
        boolean substituirCsc = request.csc() != null;
        byte[] plain = substituirCsc ? request.csc().getBytes(StandardCharsets.UTF_8) : null;
        try {
            FiscalEncryptionService.Encrypted encrypted = plain == null || plain.length == 0
                    ? null : encryptionService.encrypt(plain);
            config.atualizar(normalizar(request.inscricaoEstadual()), request.regimeTributario(), request.ambiente(),
                    request.serieNfe(), request.serieNfce(), normalizar(request.cscId()),
                    encrypted == null ? null : encrypted.cipherText(), encrypted == null ? null : encrypted.nonce(),
                    substituirCsc, request.fiscalHabilitado(), request.nfeHabilitada(),
                    request.nfceHabilitada(), request.nfseHabilitada());
            ConfiguracaoFiscalResponse response = toResponse(repository.save(config));
            auditService.registrar(empresaId, null, "CONFIGURACAO_FISCAL_ALTERADA", ator, "SUCESSO",
                    "ambiente=" + request.ambiente() + ";fiscalHabilitado=" + request.fiscalHabilitado());
            return response;
        } finally {
            if (plain != null) Arrays.fill(plain, (byte) 0);
        }
    }

    private Empresa validarAcesso(Long empresaId, String ator) {
        Empresa empresa = empresaRepository.findByIdWithDono(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada.", HttpStatus.NOT_FOUND, "/api/fiscal/configuracao"));
        if (!empresa.getDono().getEmail().equals(ator))
            throw new ApiException("Sem permissão para a configuração fiscal desta empresa.", HttpStatus.FORBIDDEN, "/api/fiscal/configuracao");
        return empresa;
    }
    private ConfiguracaoFiscalResponse toResponse(ConfiguracaoFiscalEmpresa c) {
        return new ConfiguracaoFiscalResponse(c.getEmpresaId(), c.getInscricaoEstadual(), c.getRegimeTributario(),
                c.getAmbiente(), c.getSerieNfe(), c.getSerieNfce(), c.getCscId(), c.getCscCifrado() != null,
                c.isFiscalHabilitado(), c.isNfeHabilitada(), c.isNfceHabilitada(), c.isNfseHabilitada(), c.getAtualizadoEm());
    }
    private void validarFlags(ConfiguracaoFiscalRequest request) {
        if (!request.fiscalHabilitado()
                && (request.nfeHabilitada() || request.nfceHabilitada() || request.nfseHabilitada()))
            throw new ApiException("Habilite o módulo fiscal antes de habilitar um documento.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "/api/fiscal/configuracao");
        if (request.ambiente() == ConfiguracaoFiscalEmpresa.Ambiente.PRODUCAO && request.nfseHabilitada())
            throw new ApiException("A emissão de NFS-e em produção ainda não foi liberada.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "/api/fiscal/configuracao");
    }
    private String normalizar(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private boolean preenchido(String value) { return value != null && !value.isBlank(); }
    private String somenteDigitos(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private boolean serieValida(String value) { return value != null && value.matches("[1-9][0-9]{0,2}"); }
    private ProntidaoFiscalResponse.Requisito requisito(String codigo, String descricao, boolean concluido) {
        return new ProntidaoFiscalResponse.Requisito(codigo, descricao, concluido);
    }
}
