package br.com.gestpro.auth.controller;

import br.com.gestpro.auth.dto.updatePassword.ForgotPasswordRequest;
import br.com.gestpro.auth.dto.updatePassword.ResetPasswordRequest;
import br.com.gestpro.auth.service.UpdatePasswordService;
import br.com.gestpro.infra.security.DistributedRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UpdatePasswordController {

    private final UpdatePasswordService updatePasswordService;
    private final DistributedRateLimitService rateLimit;

    public UpdatePasswordController(
            UpdatePasswordService updatePasswordService,
            DistributedRateLimitService rateLimit
    ) {
        this.updatePasswordService = updatePasswordService;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/esqueceu-senha")
    public ResponseEntity<Map<String, Object>> enviarCodigo(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimit.verificarIdentidade(DistributedRateLimitService.Operacao.RECUPERACAO_SENHA,
                request.email(), httpRequest.getRemoteAddr(), "/api/auth/esqueceu-senha");
        updatePasswordService.sendVerificationCode(request.email());

        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem",
                "Se o e-mail estiver cadastrado, enviaremos um código."
        ));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Map<String, Object>> redefinirSenha(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        updatePasswordService.resetPassword(
                request.email(),
                request.codigo(),
                request.novaSenha()
        );

        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Senha atualizada."
        ));
    }
}
