package br.com.gestpro.auth.controller;

import br.com.gestpro.auth.dto.AuthDTO.CadastroRequestDTO;
import br.com.gestpro.auth.dto.AuthDTO.LoginResponse;
import br.com.gestpro.auth.dto.AuthDTO.LoginUsuarioDTO;
import br.com.gestpro.auth.service.AuthenticationService;
import br.com.gestpro.auth.service.AuthCookieService;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.infra.security.DistributedRateLimitService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationService authService;
    private final AuthCookieService authCookieService;
    private final String frontendUrl;
    private final String baseUrl;
    private final DistributedRateLimitService rateLimit;

    public AuthController(
            AuthenticationService authService,
            AuthCookieService authCookieService,
            @Value("${app.frontend.url}") String frontendUrl,
            @Value("${app.base-url}") String baseUrl,
            DistributedRateLimitService rateLimit
    ) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.frontendUrl = frontendUrl;
        this.baseUrl = baseUrl;
        this.rateLimit = rateLimit;
    }

    @PostMapping(
            value = "/cadastro",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> cadastrarUsuario(
            @Valid @ModelAttribute CadastroRequestDTO request
    ) throws IOException {
        authService.cadastrarManual(
                request.getNome(),
                request.getEmail().trim().toLowerCase(),
                request.getSenha(),
                request.getFoto(),
                baseUrl,
                "/auth/cadastro"
        );


        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem",
                "Se o endereço puder ser cadastrado, enviaremos uma confirmação por e-mail."
        ));
    }

    @GetMapping("/confirmar")
    public void confirmarEmail(
            @RequestParam String token,
            HttpServletResponse response
    ) throws IOException {
        String destino;

        try {
            boolean confirmado = authService.confirmarEmail(token);
            destino = frontendUrl + "/confirmar-email?status="
                    + (confirmado ? "sucesso" : "erro");
        } catch (ApiException exception) {
            destino = frontendUrl
                    + "/confirmar-email?status=erro&motivo="
                    + URLEncoder.encode(
                    "token-invalido-ou-expirado",
                    StandardCharsets.UTF_8
            );
        }

        response.sendRedirect(destino);
    }

    public record ReenvioConfirmacaoRequest(
            @NotBlank(message = "Email é obrigatório")
            @Email(message = "Email inválido")
            @Size(max = 254, message = "Email excede o tamanho permitido")
            String email
    ) {}

    @PostMapping("/reenviar-confirmacao")
    public ResponseEntity<Map<String, Object>> reenviarConfirmacao(
            @Valid @RequestBody ReenvioConfirmacaoRequest request
    ) {
        authService.reenviarConfirmacao(request.email(), baseUrl);
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Se houver uma conta pendente para este e-mail, enviaremos um novo link de confirmação."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> loginUsuario(
            @Valid @RequestBody LoginUsuarioDTO request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        rateLimit.verificarIdentidade(DistributedRateLimitService.Operacao.LOGIN,
                request.email(), servletRequest.getRemoteAddr(), "/auth/login");
        encerrarSessaoAnterior(servletRequest, response);

        try {
            return ResponseEntity.ok(
                    authService.loginManual(
                            request.email(),
                            request.senha(),
                            "/auth/login",
                            response
                    )
            );
        } catch (RuntimeException exception) {
            // Uma tentativa de troca de conta nunca deve preservar a sessão
            // anterior quando as novas credenciais forem rejeitadas.
            authCookieService.remover(response);
            authCookieService.removerSessaoTemporaria(response);
            throw exception;
        }
    }

    private void encerrarSessaoAnterior(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        authCookieService.removerSessaoTemporaria(response);
    }
}
