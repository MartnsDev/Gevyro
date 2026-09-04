package br.com.gestpro.nota.controller;

import br.com.gestpro.infra.security.DistributedRateLimitService;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/fiscal/webhook/{empresaId}") @RequiredArgsConstructor
public class FiscalWebhookConfigController {
    private final FiscalWebhookConfigService service;
    private final FiscalAuthorizationService authorization;
    private final FiscalStepUpService stepUp;
    private final DistributedRateLimitService rateLimit;

    @GetMapping
    public ApiResponse<FiscalWebhookConfigResponse> buscar(@PathVariable Long empresaId, Authentication auth) {
        return ApiResponse.ok(service.buscar(empresaId, auth.getName()));
    }
    @PutMapping
    public ResponseEntity<ApiResponse<FiscalWebhookConfigResponse>> salvar(@PathVariable Long empresaId,
            @Valid @RequestBody FiscalWebhookConfigRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest http) {
        authorization.exigir(empresaId, auth.getName(), FiscalPermission.CONFIGURAR);
        rateLimit.verificar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId,
                auth.getName(), http.getRemoteAddr(), "/api/fiscal/webhook");
        stepUp.exigirEConsumir(empresaId, auth.getName(), confirmation);
        return ResponseEntity.ok(ApiResponse.ok(service.salvar(empresaId, request, auth.getName())));
    }
}
