package br.com.gestpro.nota.controller;

import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.service.FiscalAccessManagementService;
import br.com.gestpro.nota.service.FiscalAuthorizationService;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.infra.security.DistributedRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/fiscal/acessos/{empresaId}")
@RequiredArgsConstructor
public class FiscalAccessController {
    private final FiscalAccessManagementService service;
    private final DistributedRateLimitService rateLimit;
    private final FiscalAuthorizationService authorization;
    private final br.com.gestpro.nota.service.FiscalStepUpService stepUp;

    @GetMapping
    public ApiResponse<List<FiscalAccessResponse>> listar(@PathVariable Long empresaId, Authentication auth, HttpServletRequest http) {
        authorization.exigir(empresaId, auth.getName(), FiscalPermission.GERENCIAR_ACESSOS);
        limitar(DistributedRateLimitService.Operacao.CONSULTA_FISCAL, empresaId, auth, http);
        return ApiResponse.ok(service.listar(empresaId, auth.getName()));
    }
    @PutMapping
    public ApiResponse<FiscalAccessResponse> conceder(@PathVariable Long empresaId,
            @Valid @RequestBody FiscalAccessRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest http) {
        authorization.exigir(empresaId, auth.getName(), FiscalPermission.GERENCIAR_ACESSOS);
        limitar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId, auth, http);
        stepUp.exigirEConsumir(empresaId, auth.getName(), confirmation);
        return ApiResponse.ok(service.conceder(empresaId, request, auth.getName()));
    }
    @DeleteMapping("/{acessoId}")
    public ResponseEntity<ApiResponse<Void>> revogar(@PathVariable Long empresaId, @PathVariable Long acessoId,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest http) {
        authorization.exigir(empresaId, auth.getName(), FiscalPermission.GERENCIAR_ACESSOS);
        limitar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId, auth, http);
        stepUp.exigirEConsumir(empresaId, auth.getName(), confirmation);
        service.revogar(empresaId, acessoId, auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private void limitar(DistributedRateLimitService.Operacao operacao, Long empresaId, Authentication auth, HttpServletRequest http) {
        rateLimit.verificar(operacao, empresaId, auth.getName(), http.getRemoteAddr(), "/api/fiscal/acessos");
    }
}
