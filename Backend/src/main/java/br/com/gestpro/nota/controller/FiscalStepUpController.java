package br.com.gestpro.nota.controller;

import br.com.gestpro.infra.security.DistributedRateLimitService;
import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.service.FiscalStepUpService;
import br.com.gestpro.nota.service.FiscalAuthorizationService;
import br.com.gestpro.nota.FiscalPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/fiscal/confirmacao") @RequiredArgsConstructor
public class FiscalStepUpController {
    private final FiscalStepUpService service;
    private final DistributedRateLimitService rateLimit;
    private final FiscalAuthorizationService authorization;

    @PostMapping("/{empresaId}")
    public ApiResponse<FiscalStepUpResponse> confirmar(@PathVariable Long empresaId,
            @Valid @RequestBody FiscalStepUpRequest request, Authentication auth, HttpServletRequest http) {
        authorization.exigir(empresaId, auth.getName(), FiscalPermission.VISUALIZAR);
        rateLimit.verificar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId,
                auth.getName(), http.getRemoteAddr(), "/api/fiscal/confirmacao");
        return ApiResponse.ok(service.confirmar(empresaId, auth.getName(), request.senha()));
    }
}
