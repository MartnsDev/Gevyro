package br.com.gestpro.auth.service;

import br.com.gestpro.auth.dto.AuthDTO.LoginResponse;
import br.com.gestpro.auth.dto.AuthDTO.LoginUsuarioDTO;
import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.auth.service.jwtService.JwtTokenServiceInterface;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.plano.service.VerificarPlanoOperation;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.time.Duration;

@Component
public class LoginManualOperation {

    private static final Logger logger =
            LoggerFactory.getLogger(LoginManualOperation.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenServiceInterface jwtTokenService;
    private final VerificarPlanoOperation verificarPlano;
    private final AuthCookieService authCookieService;
    private final String dummyPasswordHash;
    private final StringRedisTemplate redis;
    private static final int MAX_TENTATIVAS = 8;
    private static final Duration JANELA_TENTATIVAS = Duration.ofMinutes(15);

    public LoginManualOperation(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenServiceInterface jwtTokenService,
            VerificarPlanoOperation verificarPlano,
            AuthCookieService authCookieService,
            StringRedisTemplate redis
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.verificarPlano = verificarPlano;
        this.authCookieService = authCookieService;
        this.redis = redis;

        // Reduz diferença de tempo entre usuário existente e inexistente.
        this.dummyPasswordHash =
                passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse execute(
            LoginUsuarioDTO request,
            String path,
            HttpServletResponse response
    ) {
        String email = normalizarEmail(request.email());
        validarLimite(email, path);

        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        if (usuario == null) {
            passwordEncoder.matches(request.senha(), dummyPasswordHash);
            registrarFalha(email);
            throw credenciaisInvalidas(path);
        }

        if (!usuario.isEmailConfirmado()) {
            throw new ApiException(
                    "Confirme seu e-mail antes de entrar.",
                    HttpStatus.UNAUTHORIZED,
                    path
            );
        }

        if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
            registrarFalha(email);
            throw new ApiException(
                    "Esta conta ainda não possui senha. Use o Google ou redefina sua senha.",
                    HttpStatus.UNAUTHORIZED,
                    path
            );
        }

        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            registrarFalha(email);
            throw credenciaisInvalidas(path);
        }

        try {
            verificarPlano.validarAcessoIsolado(usuario);
        } catch (ApiException exception) {
            logger.info("Plano sem acesso durante login: {}", usuario.getId());
        }

        String token = jwtTokenService.gerarToken(usuario);
        authCookieService.adicionar(response, token);
        redis.delete(tentativasKey(email));

        return new LoginResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTipoPlano(),
                usuario.getFoto(),
                usuario.getStatusAcesso().name(),
                calcularExpiracao(usuario)
        );
    }

    private ApiException credenciaisInvalidas(String path) {
        return new ApiException(
                "E-mail ou senha inválidos.",
                HttpStatus.UNAUTHORIZED,
                path
        );
    }

    private void validarLimite(String email, String path) {
        String valor = redis.opsForValue().get(tentativasKey(email));
        if (valor != null && Integer.parseInt(valor) >= MAX_TENTATIVAS)
            throw new ApiException("Muitas tentativas. Aguarde 15 minutos e tente novamente.",
                    HttpStatus.TOO_MANY_REQUESTS, path);
    }

    private void registrarFalha(String email) {
        Long tentativas = redis.opsForValue().increment(tentativasKey(email));
        if (tentativas != null && tentativas == 1)
            redis.expire(tentativasKey(email), JANELA_TENTATIVAS);
    }

    private String tentativasKey(String email) {
        return "auth:login:tentativas:" + email;
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String calcularExpiracao(Usuario usuario) {
        if (usuario.getTipoPlano() == null) {
            return null;
        }

        if (usuario.getDataAssinaturaPlus() != null) {
            LocalDate data = usuario.getDataAssinaturaPlus()
                    .plusDays(usuario.getTipoPlano().getDuracaoDiasPadrao())
                    .toLocalDate();

            return data.toString();
        }

        if (usuario.getDataPrimeiroLogin() != null) {
            LocalDate data = usuario.getDataPrimeiroLogin()
                    .plusDays(usuario.getTipoPlano().getDuracaoDiasPadrao())
                    .toLocalDate();

            return data.toString();
        }

        return null;
    }

    private String normalizarEmailNullable(String email) {
        return email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
    }
}
