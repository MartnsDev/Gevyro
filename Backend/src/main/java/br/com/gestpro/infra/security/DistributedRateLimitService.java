package br.com.gestpro.infra.security;

import br.com.gestpro.infra.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;

@Service
public class DistributedRateLimitService {
    public enum Operacao { EMISSAO_FISCAL, CONSULTA_FISCAL, CERTIFICADO_FISCAL, EXPORTACAO_FISCAL }
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local atual = redis.call('INCR', KEYS[1])
            if atual == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            local ttl = redis.call('PTTL', KEYS[1])
            return {atual, ttl}
            """, List.class);
    private final StringRedisTemplate redis;
    private final int emissao; private final int consulta; private final int certificado; private final int exportacao;

    public DistributedRateLimitService(StringRedisTemplate redis,
            @Value("${app.rate-limit.fiscal.emissao-per-minute:30}") int emissao,
            @Value("${app.rate-limit.fiscal.consulta-per-minute:120}") int consulta,
            @Value("${app.rate-limit.fiscal.certificado-per-hour:5}") int certificado,
            @Value("${app.rate-limit.fiscal.exportacao-per-minute:10}") int exportacao) {
        this.redis = redis; this.emissao = positivo(emissao); this.consulta = positivo(consulta);
        this.certificado = positivo(certificado); this.exportacao = positivo(exportacao);
    }

    public void verificar(Operacao operacao, Long empresaId, String usuario, String ip, String path) {
        if (empresaId == null) throw new ApiException("Empresa é obrigatória para controle fiscal.", HttpStatus.BAD_REQUEST, path);
        int limite = switch (operacao) {
            case EMISSAO_FISCAL -> emissao; case CONSULTA_FISCAL -> consulta;
            case CERTIFICADO_FISCAL -> certificado; case EXPORTACAO_FISCAL -> exportacao;
        };
        Duration janela = operacao == Operacao.CERTIFICADO_FISCAL ? Duration.ofHours(1) : Duration.ofMinutes(1);
        String chave = "ratelimit:fiscal:" + operacao.name().toLowerCase(Locale.ROOT) + ":" + empresaId + ":"
                + digest(normalizar(usuario)) + ":" + digest(normalizar(ip));
        try {
            List<?> resultado = redis.execute(SCRIPT, List.of(chave), String.valueOf(limite), String.valueOf(janela.toMillis()));
            if (resultado == null || resultado.size() != 2) throw new RedisConnectionFailureException("Resposta Redis inválida");
            long atual = ((Number) resultado.get(0)).longValue();
            long ttl = ((Number) resultado.get(1)).longValue();
            if (atual > limite) throw new RateLimitExceededException((ttl + 999) / 1000, path);
        } catch (RateLimitExceededException e) { throw e; }
        catch (RuntimeException indisponivel) {
            throw new ApiException("O controle distribuído de segurança está temporariamente indisponível.",
                    HttpStatus.SERVICE_UNAVAILABLE, path);
        }
    }
    private static int positivo(int value) { if (value < 1) throw new IllegalArgumentException("Rate limit deve ser positivo."); return value; }
    private String normalizar(String value) { return value == null || value.isBlank() ? "desconhecido" : value.trim().toLowerCase(Locale.ROOT); }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
