package br.com.gestpro.nota.service;

import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.nota.dto.FiscalStepUpResponse;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;

@Service
public class FiscalStepUpService {
    static final Duration TTL = Duration.ofMinutes(5);
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final FiscalAuthorizationService authorization;
    private final SecureRandom random = new SecureRandom();

    public FiscalStepUpService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
            StringRedisTemplate redis, FiscalAuthorizationService authorization) {
        this.usuarios = usuarios; this.passwordEncoder = passwordEncoder; this.redis = redis; this.authorization = authorization;
    }

    public FiscalStepUpResponse confirmar(Long empresaId, String email, String senha) {
        authorization.exigir(empresaId, email, FiscalPermission.VISUALIZAR);
        var usuario = usuarios.findByEmail(email).orElseThrow(this::credencialInvalida);
        String hash = usuario.getSenha();
        if (hash == null || !passwordEncoder.matches(senha, hash)) throw credencialInvalida();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Arrays.fill(bytes, (byte) 0);
        try {
            redis.opsForValue().set(chave(token), usuario.getId() + ":" + empresaId, TTL);
        } catch (RuntimeException e) { throw indisponivel(); }
        return new FiscalStepUpResponse(token, TTL.toSeconds());
    }

    public void exigirEConsumir(Long empresaId, String email, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw confirmacaoNecessaria();
        var usuario = usuarios.findByEmail(email).orElseThrow(this::confirmacaoNecessaria);
        try {
            String valor = redis.opsForValue().getAndDelete(chave(token));
            if (!Objects.equals(valor, usuario.getId() + ":" + empresaId)) throw confirmacaoNecessaria();
        } catch (ApiException e) { throw e; }
        catch (RuntimeException e) { throw indisponivel(); }
    }

    private String chave(String token) { return "fiscal:step-up:" + sha256(token); }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ApiException credencialInvalida() { return new ApiException("Credencial inválida.", HttpStatus.UNAUTHORIZED, "/api/fiscal/confirmacao"); }
    private ApiException confirmacaoNecessaria() { return new ApiException("Confirmação de identidade inválida ou expirada.", HttpStatus.PRECONDITION_REQUIRED, "/api/fiscal"); }
    private ApiException indisponivel() { return new ApiException("A confirmação de segurança está temporariamente indisponível.", HttpStatus.SERVICE_UNAVAILABLE, "/api/fiscal"); }
}
