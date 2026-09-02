package br.com.gestpro.nota.controller;

import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.service.ConfiguracaoFiscalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fiscal/configuracao")
@RequiredArgsConstructor
public class ConfiguracaoFiscalController {
    private final ConfiguracaoFiscalService service;

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
            @Valid @RequestBody ConfiguracaoFiscalRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(service.salvar(empresaId, request, auth.getName())));
    }
}
