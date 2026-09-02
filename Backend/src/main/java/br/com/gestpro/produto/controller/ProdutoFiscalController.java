package br.com.gestpro.produto.controller;

import br.com.gestpro.infra.security.DistributedRateLimitService;
import br.com.gestpro.produto.dto.*;
import br.com.gestpro.produto.service.ProdutoFiscalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/produtos/{produtoId}/fiscal")
@RequiredArgsConstructor
public class ProdutoFiscalController {
    private final ProdutoFiscalService service;
    private final DistributedRateLimitService rateLimit;

    @PostMapping
    public ResponseEntity<ProdutoFiscalResponse> criar(@PathVariable Long produtoId,
            @Valid @RequestBody ProdutoFiscalRequest request, Authentication auth, HttpServletRequest http) {
        Long empresaId = service.empresaIdAutorizada(produtoId, auth.getName());
        rateLimit.verificar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, empresaId,
                auth.getName(), http.getRemoteAddr(), "/api/v1/produtos/fiscal");
        ProdutoFiscalResponse criado = service.criarVersao(produtoId, request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @GetMapping
    public ProdutoFiscalResponse vigente(@PathVariable Long produtoId,
            @RequestParam(required=false) LocalDate data, Authentication auth) {
        return service.vigente(produtoId, data, auth.getName());
    }

    @GetMapping("/historico")
    public List<ProdutoFiscalResponse> historico(@PathVariable Long produtoId, Authentication auth) {
        return service.historico(produtoId, auth.getName());
    }
}
