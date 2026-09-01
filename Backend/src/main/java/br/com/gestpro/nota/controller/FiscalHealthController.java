package br.com.gestpro.nota.controller;

import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.service.FiscalHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/fiscal/health") @RequiredArgsConstructor
public class FiscalHealthController {
    private final FiscalHealthService service;
    @GetMapping
    public ResponseEntity<ApiResponse<FiscalHealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.ok(service.obter()));
    }
}
