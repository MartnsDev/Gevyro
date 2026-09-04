package br.com.gestpro.nota.controller;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.model.*;
import br.com.gestpro.nota.service.NotaFiscalInterface;
import br.com.gestpro.nota.service.NotaFiscalServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import br.com.gestpro.infra.security.DistributedRateLimitService;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nota-fiscal")
@RequiredArgsConstructor
public class NotaFiscalController {

    private final NotaFiscalInterface notaFiscalService;
    private final NotaFiscalServiceImpl notaFiscalServiceImpl;
    private final br.com.gestpro.nota.service.FiscalAuditService fiscalAuditService;
    private final br.com.gestpro.nota.service.FiscalEmissionQueueService fiscalEmissionQueueService;
    private final DistributedRateLimitService rateLimit;
    private final br.com.gestpro.nota.service.NfeXmlImportService nfeXmlImportService;
    private final br.com.gestpro.nota.service.validacoes.Listar listarNotas;
    private final br.com.gestpro.nota.service.CertificateService certificateService;
    private final br.com.gestpro.nota.service.FiscalStepUpService fiscalStepUp;
    private final br.com.gestpro.nota.service.FiscalDeliveryService fiscalDeliveryService;

    // CRUD

    @PostMapping
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> criar(
            @jakarta.validation.Valid @RequestBody CriarNotaRequest request, Authentication auth) {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(request.getEmpresaId(), auth.getName(), FiscalPermission.EMITIR);
        NotaFiscal nota = notaFiscalService.criar(request);
        fiscalAuditService.registrar(nota.getEmpresaId(), nota.getId(), "DOCUMENTO_CRIADO", auth.getName(), "SUCESSO", null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    @PostMapping("/vendas/{vendaId}/nfce")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> criarNfceDaVenda(
            @PathVariable Long vendaId, Authentication auth, HttpServletRequest httpRequest) {
        Long empresaId = notaFiscalServiceImpl.validarAcessoVendaFiscal(vendaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, empresaId, auth, httpRequest,
                "/api/nota-fiscal/vendas/nfce");
        NotaFiscal nota = notaFiscalService.criarNfceDaVenda(vendaId, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> buscarPorId(@PathVariable Long id, Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.validarAcessoNota(id, auth.getName());
        NotaFiscal nota = notaFiscalService.buscarPorId(id);
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, nota.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal");
        return ResponseEntity.ok(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listar(
            @ModelAttribute FilterNotaFiscalDTO filtro, Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.validarAcessoEmpresa(filtro.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, filtro.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal");
        return ResponseEntity.ok(ApiResponse.ok(listarNotas.listar(filtro)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> excluir(@PathVariable Long id, Authentication auth) {
        notaFiscalServiceImpl.exigirPermissaoNota(id, auth.getName(), FiscalPermission.EMITIR);
        NotaFiscal nota = notaFiscalService.buscarPorId(id);
        notaFiscalService.excluir(id);
        fiscalAuditService.registrar(nota.getEmpresaId(), id, "RASCUNHO_EXCLUIDO", auth.getName(), "SUCESSO", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // CICLO DE VIDA

    @PostMapping("/{id}/emitir")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> emitir(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.exigirPermissaoNota(id, auth.getName(), FiscalPermission.EMITIR);
        NotaFiscal nota = fiscalEmissionQueueService.enfileirar(id, idempotencyKey, auth.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    @PostMapping("/cancelar")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> cancelar(@jakarta.validation.Valid @RequestBody CancelarNotaRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(request.getNotaId());
        notaFiscalServiceImpl.exigirPermissaoEmpresa(acesso.getEmpresaId(), auth.getName(), FiscalPermission.CANCELAR);
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, acesso.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/cancelar");
        fiscalStepUp.exigirEConsumir(acesso.getEmpresaId(), auth.getName(), confirmation);
        NotaFiscal nota = notaFiscalService.cancelar(request);
        fiscalAuditService.registrar(nota.getEmpresaId(), nota.getId(), "DOCUMENTO_CANCELADO", auth.getName(), "SUCESSO", null);
        return ResponseEntity.ok(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    @PostMapping("/carta-correcao")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cartaCorrecao(
            @jakarta.validation.Valid @RequestBody CartaCorrecaoRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(request.getNotaId());
        notaFiscalServiceImpl.exigirPermissaoEmpresa(acesso.getEmpresaId(), auth.getName(), FiscalPermission.CANCELAR);
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, acesso.getEmpresaId(), auth, httpRequest,
                "/api/nota-fiscal/carta-correcao");
        fiscalStepUp.exigirEConsumir(acesso.getEmpresaId(), auth.getName(), confirmation);
        EventoFiscal evento = notaFiscalService.cartaCorrecao(request);
        fiscalAuditService.registrar(acesso.getEmpresaId(), acesso.getId(), "CCE_REGISTRADA", auth.getName(),
                "SUCESSO", "sequencia=" + evento.getSequencia() + ",protocolo=" + evento.getProtocolo());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("sequencia", evento.getSequencia(),
                "protocolo", evento.getProtocolo() == null ? "" : evento.getProtocolo())));
    }

    @PostMapping("/inutilizar")
    public ResponseEntity<ApiResponse<Void>> inutilizar(@jakarta.validation.Valid @RequestBody InutilizarRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(request.getEmpresaId(), auth.getName(), FiscalPermission.INUTILIZAR);
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, request.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/inutilizar");
        fiscalStepUp.exigirEConsumir(request.getEmpresaId(), auth.getName(), confirmation);
        notaFiscalService.inutilizar(request);
        fiscalAuditService.registrar(request.getEmpresaId(), null, "NUMERACAO_INUTILIZADA", auth.getName(), "SUCESSO",
                "tipo=" + request.getTipo() + ",serie=" + request.getSerie() + ",faixa=" + request.getNumeroInicio() + "-" + request.getNumeroFim());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/transmitir-contingencias")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> transmitirContingencias(@RequestParam Long empresaId,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(empresaId, auth.getName(), FiscalPermission.EMITIR);
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal/transmitir-contingencias");
        fiscalStepUp.exigirEConsumir(empresaId, auth.getName(), confirmation);
        int qtd = notaFiscalService.transmitirContingencias(empresaId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("transmitidas", qtd)));
    }

    @PostMapping("/{id}/contingencia-offline")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> contingenciaOffline(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody ContingenciaOfflineRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(id);
        notaFiscalServiceImpl.exigirPermissaoEmpresa(acesso.getEmpresaId(), auth.getName(), FiscalPermission.EMITIR);
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, acesso.getEmpresaId(), auth, httpRequest,
                "/api/nota-fiscal/contingencia-offline");
        fiscalStepUp.exigirEConsumir(acesso.getEmpresaId(), auth.getName(), confirmation);
        NotaFiscal nota = notaFiscalService.emitirContingenciaOffline(id, request.justificativa());
        fiscalAuditService.registrar(nota.getEmpresaId(), nota.getId(), "CONTINGENCIA_OFFLINE_GERADA",
                auth.getName(), "SUCESSO", null);
        return ResponseEntity.ok(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    // DOWNLOADS

    @PostMapping(value = "/importar-xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> importarXml(
            @RequestParam Long empresaId, @RequestParam("arquivo") MultipartFile arquivo,
            Authentication auth, HttpServletRequest httpRequest) throws java.io.IOException {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(empresaId, auth.getName(), FiscalPermission.EMITIR);
        limitar(DistributedRateLimitService.Operacao.EXPORTACAO_FISCAL, empresaId, auth, httpRequest,
                "/api/nota-fiscal/importar-xml");
        String nome = arquivo == null ? null : arquivo.getOriginalFilename();
        String mime = arquivo == null ? null : arquivo.getContentType();
        if (arquivo == null || arquivo.isEmpty() || arquivo.getSize() > br.com.gestpro.nota.service.NfeXmlImportService.MAX_XML_BYTES
                || nome == null || !nome.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")
                || !(MediaType.APPLICATION_XML_VALUE.equalsIgnoreCase(mime) || MediaType.TEXT_XML_VALUE.equalsIgnoreCase(mime)))
            throw new br.com.gestpro.infra.exception.ApiException("Envie um arquivo XML válido de até 2 MiB.",
                    HttpStatus.BAD_REQUEST, "/api/nota-fiscal/importar-xml");
        byte[] bytes = arquivo.getBytes();
        try {
            NotaFiscal nota = nfeXmlImportService.importar(empresaId, bytes);
            fiscalAuditService.registrar(empresaId, nota.getId(), "XML_IMPORTADO", auth.getName(), "SUCESSO", null);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
        } finally { java.util.Arrays.fill(bytes, (byte) 0); }
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id, Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(id);
        notaFiscalServiceImpl.validarAcessoEmpresa(acesso.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, acesso.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/xml");
        byte[] xml = notaFiscalService.baixarXml(id);
        NotaFiscal nota = notaFiscalService.buscarPorId(id);
        fiscalAuditService.registrar(nota.getEmpresaId(), id, "XML_BAIXADO", auth.getName(), "SUCESSO", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDisposition(ContentDisposition.attachment().filename("nfe-" + id + ".xml").build());
        return ResponseEntity.ok().headers(headers).body(xml);
    }

    @GetMapping("/{id}/danfe")
    public ResponseEntity<Object> baixarDanfe(@PathVariable Long id, Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(id);
        notaFiscalServiceImpl.validarAcessoEmpresa(acesso.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, acesso.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/danfe");
        byte[] pdf = notaFiscalService.gerarDanfePdf(id);
        fiscalAuditService.registrar(acesso.getEmpresaId(), id, "DANFE_BAIXADO", auth.getName(), "SUCESSO", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("danfe-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @PostMapping("/{id}/entregas/email")
    public ResponseEntity<ApiResponse<FiscalDeliveryResponse>> solicitarEntregaEmail(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody FiscalEmailDeliveryRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(id);
        notaFiscalServiceImpl.exigirPermissaoEmpresa(acesso.getEmpresaId(), auth.getName(), FiscalPermission.EXPORTAR);
        limitar(DistributedRateLimitService.Operacao.EXPORTACAO_FISCAL, acesso.getEmpresaId(), auth, httpRequest,
                "/api/nota-fiscal/entregas/email");
        fiscalStepUp.exigirEConsumir(acesso.getEmpresaId(), auth.getName(), confirmation);
        FiscalDeliveryResponse delivery = fiscalDeliveryService.solicitarEmail(id, request.destinatario(), auth.getName());
        fiscalAuditService.registrar(acesso.getEmpresaId(), id, "ENTREGA_EMAIL_SOLICITADA", auth.getName(),
                delivery.status(), null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(delivery));
    }

    // ÁREA DO CONTADOR
    @GetMapping("/exportar/xml-mensal")
    public ResponseEntity<Object> exportarXmlsMensal(
            @RequestParam Long empresaId,
            @RequestParam String periodo, Authentication auth, HttpServletRequest httpRequest
    ) {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(empresaId, auth.getName(), FiscalPermission.EXPORTAR);
        limitar(DistributedRateLimitService.Operacao.EXPORTACAO_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal/exportar/xml-mensal");
        YearMonth ym = periodoFiscal(periodo, "/api/nota-fiscal/exportar/xml-mensal");
        byte[] zip = notaFiscalService.gerarZipXmlsMensal(empresaId, ym);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("xmls-" + periodo + ".zip").build());
        return ResponseEntity.ok().headers(headers).body(zip);
    }

    @GetMapping("/exportar/sped")
    public ResponseEntity<Object> exportarSped(
            @RequestParam Long empresaId,
            @RequestParam String periodo,
            @RequestParam String tipo, Authentication auth, HttpServletRequest httpRequest
    ) {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(empresaId, auth.getName(), FiscalPermission.EXPORTAR);
        limitar(DistributedRateLimitService.Operacao.EXPORTACAO_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal/exportar/sped");
        try {
            YearMonth ym = periodoFiscal(periodo, "/api/nota-fiscal/exportar/sped");
            byte[] sped = notaFiscalService.gerarArquivoSped(empresaId, ym, tipo);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("sped-" + tipo + "-" + periodo + ".txt").build());
            return ResponseEntity.ok().headers(headers).body(sped);
        } catch (UnsupportedOperationException | java.lang.AbstractMethodError e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(ApiResponse.erro("O módulo de SPED Fiscal será liberado na próxima atualização."));
        }
    }

    // CERTIFICADO DIGITAL
    @GetMapping("/certificado/{empresaId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> consultarCertificado(
            @PathVariable Long empresaId, Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(empresaId, auth.getName(), FiscalPermission.GERENCIAR_CERTIFICADO);
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, empresaId, auth, httpRequest,
                "/api/nota-fiscal/certificado");
        return ResponseEntity.ok(ApiResponse.ok(certificateService.consultar(empresaId)));
    }

    @DeleteMapping("/certificado/{empresaId}")
    public ResponseEntity<ApiResponse<Void>> excluirCertificado(
            @PathVariable Long empresaId,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(empresaId, auth.getName(), FiscalPermission.GERENCIAR_CERTIFICADO);
        rateLimit.verificar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId,
                auth.getName(), httpRequest.getRemoteAddr(), "/api/nota-fiscal/certificado");
        fiscalStepUp.exigirEConsumir(empresaId, auth.getName(), confirmation);
        certificateService.excluir(empresaId);
        fiscalAuditService.registrar(empresaId, null, "CERTIFICADO_EXCLUIDO", auth.getName(), "SUCESSO", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/certificado/{empresaId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadCertificado(
            @PathVariable Long empresaId,
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("senha") String senha,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest httpRequest
    ) throws java.io.IOException {
        notaFiscalServiceImpl.exigirPermissaoEmpresa(empresaId, auth.getName(), FiscalPermission.GERENCIAR_CERTIFICADO);
        rateLimit.verificar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId,
                auth.getName(), httpRequest.getRemoteAddr(), "/api/nota-fiscal/certificado");
        fiscalStepUp.exigirEConsumir(empresaId, auth.getName(), confirmation);
        if (arquivo == null || arquivo.isEmpty() || arquivo.getSize() > br.com.gestpro.nota.service.CertificateService.MAX_PFX_BYTES)
            throw new br.com.gestpro.infra.exception.ApiException("Certificado A1 deve possuir no máximo 1 MiB.",
                    HttpStatus.BAD_REQUEST, "/api/nota-fiscal/certificado");
        byte[] pfxBytes = arquivo.getBytes();
        try {
            Map<String, String> info = notaFiscalServiceImpl.registrarCertificado(empresaId, pfxBytes, senha);
            fiscalAuditService.registrar(empresaId, null, "CERTIFICADO_SUBSTITUIDO", auth.getName(), "SUCESSO", null);
            return ResponseEntity.ok(ApiResponse.ok(info));
        } finally { java.util.Arrays.fill(pfxBytes, (byte) 0); }
    }

    // CONSULTAS AUXILIARES
    @GetMapping("/cep/{cep}")
    public ResponseEntity<Object> consultarCep(@PathVariable String cep, @RequestParam Long empresaId,
            Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, empresaId, auth, httpRequest,
                "/api/nota-fiscal/cep");
        Object resultado = notaFiscalService.consultarCep(cep);
        return resultado != null ? ResponseEntity.ok(resultado) : ResponseEntity.notFound().build();
    }

    @GetMapping("/cnpj/{cnpj}")
    public ResponseEntity<Object> consultarCnpj(@PathVariable String cnpj, @RequestParam Long empresaId,
            Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, empresaId, auth, httpRequest,
                "/api/nota-fiscal/cnpj");
        Object resultado = notaFiscalService.consultarCnpj(cnpj);
        return resultado != null ? ResponseEntity.ok(resultado) : ResponseEntity.notFound().build();
    }

    @GetMapping("/chave/{chaveAcesso}")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> buscarPorChave(@PathVariable String chaveAcesso, Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal nota=notaFiscalService.buscarPorChaveAcesso(chaveAcesso);
        notaFiscalServiceImpl.validarAcessoEmpresa(nota.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, nota.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/chave");
        return ResponseEntity.ok(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    @GetMapping("/estatisticas")
    public ResponseEntity<ApiResponse<EstatisticasResponse>> getEstatisticas(
            @RequestParam Long empresaId, Authentication auth, HttpServletRequest httpRequest
    ) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal/estatisticas");
        return ResponseEntity.ok(ApiResponse.ok(notaFiscalService.getEstatisticas(empresaId)));
    }

    private void limitar(DistributedRateLimitService.Operacao operacao, Long empresaId, Authentication auth,
                         HttpServletRequest request, String path) {
        rateLimit.verificar(operacao, empresaId, auth.getName(), request.getRemoteAddr(), path);
    }

    private YearMonth periodoFiscal(String value, String path) {
        try { return YearMonth.parse(value); }
        catch (java.time.format.DateTimeParseException e) {
            throw new br.com.gestpro.infra.exception.ApiException("Período deve usar o formato AAAA-MM.",
                    HttpStatus.BAD_REQUEST, path);
        }
    }
}
