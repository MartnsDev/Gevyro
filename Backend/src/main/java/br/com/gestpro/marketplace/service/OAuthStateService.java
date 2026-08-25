package br.com.gestpro.marketplace.service;

import br.com.gestpro.infra.exception.ApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class OAuthStateService {

    private static final Duration VALIDADE = Duration.ofMinutes(10);
    private static final String PREFIXO = "oauth:marketplace:state:";

    private final StringRedisTemplate redis;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthStateService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String criar(Long empresaId, String emailUsuario) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIXO + hash(state), empresaId + "\n" + emailUsuario, VALIDADE);
        return state;
    }

    public DadosState consumir(String state) {
        if (state == null || state.isBlank()) throw stateInvalido();
        String chave = PREFIXO + hash(state);
        String valor = redis.opsForValue().getAndDelete(chave);
        if (valor == null) throw stateInvalido();
        String[] partes = valor.split("\n", 2);
        if (partes.length != 2) throw stateInvalido();
        try {
            return new DadosState(Long.parseLong(partes[0]), partes[1]);
        } catch (NumberFormatException exception) {
            throw stateInvalido();
        }
    }

    private String hash(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private ApiException stateInvalido() {
        return new ApiException("Estado OAuth inválido, expirado ou já utilizado.",
                HttpStatus.BAD_REQUEST, "/api/v1/marketplace/callback");
    }

    public record DadosState(Long empresaId, String emailUsuario) {}
}
