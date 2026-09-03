package br.com.gestpro.nota.controller;

import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.service.ConfiguracaoFiscalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import br.com.gestpro.infra.security.DistributedRateLimitService;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.nota.service.FiscalAuthorizationService;
import br.com.gestpro.nota.service.FiscalStepUpService;

@RestController
@RequestMapping("/api/fiscal/configuracao")
@RequiredArgsConstructor
public class ConfiguracaoFiscalController {
    private final ConfiguracaoFiscalService service;
    private final FiscalAuthorizationService authorization;
    private final FiscalStepUpService stepUp;
    private final DistributedRateLimitService rateLimit;

    @GetMapping("/{empresaId}")
    public ResponseEntity<ApiResponse<ConfiguracaoFiscalResponse>> buscar(@PathVariable Long empresaId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(service.buscar(empresaId, auth.getName())));
    }

    @GetMapping("/{empresaId}/prontidao")
    public ResponseEntity<ApiResponse<ProntidaoFiscalResponse>> prontidao(@PathVariable Long empresaId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(service.prontidao(empresaId, auth.getName())));
    }

    @PutMapping("/{empresaId}")
    public ResponseEntity<ApiResponse<ConfiguracaoFiscalResponse>> salvar(@PathVariable Long empresaId,
            @Valid @RequestBody ConfiguracaoFiscalRequest request,
            @RequestHeader(value = "X-Fiscal-Confirmation", required = false) String confirmation,
            Authentication auth, HttpServletRequest http) {
        authorization.exigir(empresaId, auth.getName(), FiscalPermission.CONFIGURAR);
        rateLimit.verificar(DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, empresaId,
                auth.getName(), http.getRemoteAddr(), "/api/fiscal/configuracao");
        stepUp.exigirEConsumir(empresaId, auth.getName(), confirmation);
        return ResponseEntity.ok(ApiResponse.ok(service.salvar(empresaId, request, auth.getName())));
    }
}
