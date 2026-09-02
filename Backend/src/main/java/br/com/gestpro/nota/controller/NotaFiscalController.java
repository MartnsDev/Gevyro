package br.com.gestpro.nota.controller;

import br.com.gestpro.nota.NotaFiscalStatus;
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

    // CRUD

    @PostMapping
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> criar(@RequestBody CriarNotaRequest request, Authentication auth) {
        try {
            notaFiscalServiceImpl.validarAcessoEmpresa(request.getEmpresaId(), auth.getName());
            NotaFiscal nota = notaFiscalService.criar(request);
            fiscalAuditService.registrar(nota.getEmpresaId(), nota.getId(), "DOCUMENTO_CRIADO", auth.getName(), "SUCESSO", null);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.erro(e.getMessage()));
        }
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
    public ResponseEntity<ApiResponse<List<NotaFiscalResumoResponse>>> listar(
            @RequestParam Long empresaId,
            @RequestParam(required = false) NotaFiscalStatus status, Authentication auth, HttpServletRequest httpRequest
    ) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal");
        return ResponseEntity.ok(ApiResponse.ok(notaFiscalService.listar(empresaId, status)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> excluir(@PathVariable Long id, Authentication auth) {
        try {
            notaFiscalServiceImpl.validarAcessoNota(id, auth.getName());
            notaFiscalService.excluir(id);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.erro(e.getMessage()));
        }
    }

    // CICLO DE VIDA

    @PostMapping("/{id}/emitir")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> emitir(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.validarAcessoNota(id, auth.getName());
        NotaFiscal nota = fiscalEmissionQueueService.enfileirar(id, idempotencyKey, auth.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
    }

    @PostMapping("/cancelar")
    public ResponseEntity<ApiResponse<NotaFiscalResumoResponse>> cancelar(@jakarta.validation.Valid @RequestBody CancelarNotaRequest request, Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(request.getNotaId());
        notaFiscalServiceImpl.validarAcessoEmpresa(acesso.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, acesso.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/cancelar");
        try {
            NotaFiscal nota = notaFiscalService.cancelar(request);
            fiscalAuditService.registrar(nota.getEmpresaId(), nota.getId(), "DOCUMENTO_CANCELADO", auth.getName(), "SUCESSO", null);
            return ResponseEntity.ok(ApiResponse.ok(notaFiscalServiceImpl.toResumo(nota)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.erro(e.getMessage()));
        }
    }

    @PostMapping("/carta-correcao")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cartaCorrecao(
            @jakarta.validation.Valid @RequestBody CartaCorrecaoRequest request,
            Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(request.getNotaId());
        notaFiscalServiceImpl.validarAcessoEmpresa(acesso.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, acesso.getEmpresaId(), auth, httpRequest,
                "/api/nota-fiscal/carta-correcao");
        EventoFiscal evento = notaFiscalService.cartaCorrecao(request);
        fiscalAuditService.registrar(acesso.getEmpresaId(), acesso.getId(), "CCE_REGISTRADA", auth.getName(),
                "SUCESSO", "sequencia=" + evento.getSequencia() + ",protocolo=" + evento.getProtocolo());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("sequencia", evento.getSequencia(),
                "protocolo", evento.getProtocolo() == null ? "" : evento.getProtocolo())));
    }

    @PostMapping("/inutilizar")
    public ResponseEntity<ApiResponse<Void>> inutilizar(@jakarta.validation.Valid @RequestBody InutilizarRequest request, Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.validarAcessoEmpresa(request.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, request.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/inutilizar");
        notaFiscalService.inutilizar(request);
        fiscalAuditService.registrar(request.getEmpresaId(), null, "NUMERACAO_INUTILIZADA", auth.getName(), "SUCESSO",
                "tipo=" + request.getTipo() + ",serie=" + request.getSerie() + ",faixa=" + request.getNumeroInicio() + "-" + request.getNumeroFim());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/transmitir-contingencias")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> transmitirContingencias(@RequestParam Long empresaId, Authentication auth, HttpServletRequest httpRequest) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal/transmitir-contingencias");
        int qtd = notaFiscalService.transmitirContingencias(empresaId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("transmitidas", qtd)));
    }

    // DOWNLOADS

    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id, Authentication auth, HttpServletRequest httpRequest) {
        NotaFiscal acesso = notaFiscalService.buscarPorId(id);
        notaFiscalServiceImpl.validarAcessoEmpresa(acesso.getEmpresaId(), auth.getName());
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, acesso.getEmpresaId(), auth, httpRequest, "/api/nota-fiscal/xml");
        try {
            byte[] xml = notaFiscalService.baixarXml(id);
            NotaFiscal nota = notaFiscalService.buscarPorId(id);
            fiscalAuditService.registrar(nota.getEmpresaId(), id, "XML_BAIXADO", auth.getName(), "SUCESSO", null);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setContentDisposition(ContentDisposition.attachment().filename("nfe-" + id + ".xml").build());
            return ResponseEntity.ok().headers(headers).body(xml);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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

    // ÁREA DO CONTADOR
    @GetMapping("/exportar/xml-mensal")
    public ResponseEntity<Object> exportarXmlsMensal(
            @RequestParam Long empresaId,
            @RequestParam String periodo, Authentication auth, HttpServletRequest httpRequest
    ) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.EXPORTACAO_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal/exportar/xml-mensal");
        try {
            YearMonth ym = YearMonth.parse(periodo);
            byte[] zip = notaFiscalService.gerarZipXmlsMensal(empresaId, ym);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/zip"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("xmls-" + periodo + ".zip").build());
            return ResponseEntity.ok().headers(headers).body(zip);
        } catch (Exception e) {
            // Agora devolvemos o JSON de erro!
            String msg = e.getMessage() != null ? e.getMessage() : "Nenhuma nota encontrada para o período selecionado.";
            return ResponseEntity.badRequest().body(ApiResponse.erro(msg));
        }
    }

    @GetMapping("/exportar/sped")
    public ResponseEntity<Object> exportarSped(
            @RequestParam Long empresaId,
            @RequestParam String periodo,
            @RequestParam String tipo, Authentication auth, HttpServletRequest httpRequest
    ) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        limitar(DistributedRateLimitService.Operacao.EXPORTACAO_FISCAL, empresaId, auth, httpRequest, "/api/nota-fiscal/exportar/sped");
        try {
            YearMonth ym = YearMonth.parse(periodo);
            byte[] sped = notaFiscalService.gerarArquivoSped(empresaId, ym, tipo);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("sped-" + tipo + "-" + periodo + ".txt").build());
            return ResponseEntity.ok().headers(headers).body(sped);
        } catch (UnsupportedOperationException | java.lang.AbstractMethodError e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(ApiResponse.erro("O módulo de SPED Fiscal será liberado na próxima atualização."));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.erro("Erro ao gerar SPED: " + e.getMessage()));
        }
    }

    // CERTIFICADO DIGITAL
    @PostMapping("/certificado/{empresaId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadCertificado(
            @PathVariable Long empresaId,
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("senha") String senha, Authentication auth, HttpServletRequest httpRequest
    ) {
        notaFiscalServiceImpl.validarAcessoEmpresa(empresaId, auth.getName());
        rateLimit.verificar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId,
                auth.getName(), httpRequest.getRemoteAddr(), "/api/nota-fiscal/certificado");
        try {
            if (arquivo == null || arquivo.isEmpty() || arquivo.getSize() > br.com.gestpro.nota.service.CertificateService.MAX_PFX_BYTES)
                return ResponseEntity.badRequest().body(ApiResponse.erro("Certificado A1 deve possuir no máximo 1 MiB."));
            byte[] pfxBytes = arquivo.getBytes();
            try {
                Map<String, String> info = notaFiscalServiceImpl.registrarCertificado(empresaId, pfxBytes, senha);
                fiscalAuditService.registrar(empresaId, null, "CERTIFICADO_SUBSTITUIDO", auth.getName(), "SUCESSO", null);
                return ResponseEntity.ok(ApiResponse.ok(info));
            } finally { java.util.Arrays.fill(pfxBytes, (byte) 0); }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.erro(e.getMessage()));
        }
    }

    // CONSULTAS AUXILIARES
    @GetMapping("/cep/{cep}")
    public ResponseEntity<Object> consultarCep(@PathVariable String cep) {
        Object resultado = notaFiscalService.consultarCep(cep);
        return resultado != null ? ResponseEntity.ok(resultado) : ResponseEntity.notFound().build();
    }

    @GetMapping("/cnpj/{cnpj}")
    public ResponseEntity<Object> consultarCnpj(@PathVariable String cnpj) {
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
}
