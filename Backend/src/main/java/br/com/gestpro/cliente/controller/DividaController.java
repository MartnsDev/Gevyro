package br.com.gestpro.cliente.controller;

import br.com.gestpro.cliente.dto.DividaDTO;
import br.com.gestpro.cliente.dto.DividaRequest;
import br.com.gestpro.cliente.service.DividaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/dividas")
@RequiredArgsConstructor
public class DividaController {

    private final DividaService dividaService;

    @PostMapping
    public ResponseEntity<DividaDTO> criar(@Valid @RequestBody DividaRequest req, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dividaService.criar(req, authentication.getName()));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<DividaDTO>> listar(@PathVariable Long clienteId, Authentication authentication) {
        return ResponseEntity.ok(dividaService.listarPorCliente(clienteId, authentication.getName()));
    }

    @PatchMapping("/{id}/pagamento")
    public ResponseEntity<DividaDTO> pagar(
            @PathVariable Long id,
            @RequestParam BigDecimal valor,
            Authentication authentication) {
        return ResponseEntity.ok(dividaService.registrarPagamento(id, valor, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, Authentication authentication) {
        dividaService.excluir(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
