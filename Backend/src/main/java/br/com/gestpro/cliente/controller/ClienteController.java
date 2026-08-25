package br.com.gestpro.cliente.controller;

import br.com.gestpro.cliente.dto.ClienteDTO;
import br.com.gestpro.cliente.dto.ClienteRequest;
import br.com.gestpro.cliente.service.ClienteServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteServiceImpl service;

    @PostMapping
    public ResponseEntity<ClienteDTO> criar(
            @Valid @RequestBody ClienteRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.criar(req, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ClienteRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(service.atualizar(id, req, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO> buscarPorId(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(service.buscarPorId(id, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<ClienteDTO>> listar(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) String tipo,
            Authentication authentication) {
        if (empresaId != null && tipo != null)
            return ResponseEntity.ok(service.listarPorEmpresaETipo(empresaId, tipo, authentication.getName()));
        if (empresaId != null)
            return ResponseEntity.ok(service.listarPorEmpresa(empresaId, authentication.getName()));
        return ResponseEntity.ok(service.listarAtivos(authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(
            @PathVariable Long id,
            Authentication authentication) {
        service.desativar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/criar")
    public ResponseEntity<ClienteDTO> criarLegado(
            @Valid @RequestBody ClienteRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.criar(req, authentication.getName()));
    }

    @GetMapping("/listar")
    public ResponseEntity<List<ClienteDTO>> listarLegado(
            @RequestParam(required = false) Long empresaId,
            Authentication authentication) {
        if (empresaId != null) return ResponseEntity.ok(service.listarPorEmpresa(empresaId, authentication.getName()));
        return ResponseEntity.ok(service.listarAtivos(authentication.getName()));
    }

    @DeleteMapping("/desativar/{id}")
    public ResponseEntity<Void> desativarLegado(@PathVariable Long id, Authentication authentication) {
        service.desativar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
