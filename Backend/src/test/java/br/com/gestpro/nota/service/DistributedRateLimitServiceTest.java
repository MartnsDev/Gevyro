package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.*;
import br.com.gestpro.infra.security.DistributedRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DistributedRateLimitServiceTest {
    @Test
    void permiteDentroDoLimiteEUsaRedisAtomico() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 60_000L));
        var service = service(redis);

        service.verificar(DistributedRateLimitService.Operacao.EMISSAO_FISCAL, 3L,
                "dono@empresa.com", "127.0.0.1", "/emitir");

        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void excedidoInformaTempoParaNovaTentativa() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(31L, 12_400L));

        assertThatThrownBy(() -> service(redis).verificar(
                DistributedRateLimitService.Operacao.EMISSAO_FISCAL, 3L, "ator", "ip", "/emitir"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        erro -> assertThat(erro.getRetryAfterSeconds()).isEqualTo(13));
    }

    @Test
    void falhaFechadaQuandoRedisNaoPodeGarantirLimiteFiscal() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("offline"));

        assertThatThrownBy(() -> service(redis).verificar(
                DistributedRateLimitService.Operacao.CERTIFICADO_FISCAL, 3L, "ator", "ip", "/certificado"))
                .isInstanceOfSatisfying(ApiException.class,
                        erro -> assertThat(erro.getStatus().value()).isEqualTo(503));
    }

    @Test
    void limitaLoginPorIdentidadeEIpEmCincoPorMinuto() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(6L, 42_000L));

        assertThatThrownBy(() -> service(redis).verificarIdentidade(
                DistributedRateLimitService.Operacao.LOGIN, "Pessoa@Example.com", "203.0.113.4", "/auth/login"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        erro -> assertThat(erro.getRetryAfterSeconds()).isEqualTo(42));
    }

    @Test
    void limitaRecuperacaoEmJanelaDeQuinzeMinutos() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(4L, 899_001L));

        assertThatThrownBy(() -> service(redis).verificarIdentidade(
                DistributedRateLimitService.Operacao.RECUPERACAO_SENHA,
                "pessoa@example.com", "203.0.113.4", "/api/auth/esqueceu-senha"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        erro -> assertThat(erro.getRetryAfterSeconds()).isEqualTo(900));
    }

    private DistributedRateLimitService service(StringRedisTemplate redis) {
        return new DistributedRateLimitService(redis, 5, 3, 30, 120, 5, 10);
    }
}
