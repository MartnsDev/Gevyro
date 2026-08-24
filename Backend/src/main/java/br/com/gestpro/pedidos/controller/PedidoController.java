package br.com.gestpro.pedidos.controller;

import br.com.gestpro.pedidos.dto.PedidoResponseDTO;
import br.com.gestpro.pedidos.dto.RegistrarPedidoDTO;
import br.com.gestpro.pedidos.dto.StatusPedido;
import br.com.gestpro.pedidos.service.PedidoServiceInterface;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints do módulo "Pedidos" — vendas rápidas sem caixa físico.
 *
 * POST   /empresa/{empresaId}              → registrar pedido
 * GET    /empresa/{empresaId}              → listar pedidos da empresa
 * DELETE /empresa/{empresaId}/historico    → apagar TODO o histórico da empresa
 * GET    /{id}                             → buscar por ID
 * PATCH  /{id}/status                     → atualizar status
 * POST   /{id}/cancelar                   → cancelar + devolver estoque
 * PATCH  /{id}/observacao                 → editar observação
 * DELETE /{id}                            → remover pedido individual do histórico
 */
@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoServiceInterface pedidoService;

    @PostMapping("/empresa/{empresaId}")
    public ResponseEntity<PedidoResponseDTO> registrar(
            @PathVariable Long empresaId,
            @Valid @RequestBody RegistrarPedidoDTO dto,
            Authentication authentication) {

        dto.setEmailUsuario(authentication.getName());
        dto.setEmpresaId(empresaId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new PedidoResponseDTO(pedidoService.registrarPedido(dto)));
    }


    @GetMapping("/empresa/{empresaId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PedidoResponseDTO>> listarPorEmpresa(
            @PathVariable Long empresaId,
            Authentication authentication) {

        return ResponseEntity.ok(
                pedidoService.listarPorEmpresa(empresaId, authentication.getName())
                        .stream()
                        .map(PedidoResponseDTO::new)
                        .toList()
        );
    }

    @DeleteMapping("/empresa/{empresaId}/historico")
    public ResponseEntity<Void> limparHistorico(
            @PathVariable Long empresaId,
            Authentication authentication) {

        pedidoService.limparHistorico(empresaId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<PedidoResponseDTO> buscarPorId(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(new PedidoResponseDTO(
                pedidoService.buscarPorId(id, authentication.getName())));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PedidoResponseDTO> atualizarStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) return ResponseEntity.badRequest().build();

        StatusPedido novoStatus;
        try {
            novoStatus = StatusPedido.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(new PedidoResponseDTO(
                pedidoService.atualizarStatus(id, novoStatus, authentication.getName())
        ));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        String motivo = body != null ? body.getOrDefault("motivo", "") : "";
        return ResponseEntity.ok(new PedidoResponseDTO(
                pedidoService.cancelarPedido(id, motivo, authentication.getName())
        ));
    }

    @PatchMapping("/{id}/observacao")
    public ResponseEntity<PedidoResponseDTO> editarObservacao(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        String obs = body.getOrDefault("observacao", "");
        return ResponseEntity.ok(new PedidoResponseDTO(
                pedidoService.editarObservacao(id, obs, authentication.getName())
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(
            @PathVariable Long id,
            Authentication authentication) {

        pedidoService.removerPedido(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
