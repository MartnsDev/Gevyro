package br.com.gestpro.nota.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FiscalStepUpServiceTest {
    @Test void emiteTokenOpacoCurtoEConsomeUmaUnicaVez() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        FiscalAuthorizationService authorization = mock(FiscalAuthorizationService.class);
        Usuario usuario = new Usuario(); usuario.setId(8L); usuario.setEmail("dono@empresa.test"); usuario.setSenha("hash");
        when(usuarios.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(encoder.matches("senha-correta", "hash")).thenReturn(true);
        when(redis.opsForValue()).thenReturn(values);
        var service = new FiscalStepUpService(usuarios, encoder, redis, authorization);

        var resposta = service.confirmar(3L, usuario.getEmail(), "senha-correta");

        assertThat(resposta.token()).matches("[A-Za-z0-9_-]{43}");
        verify(values).set(startsWith("fiscal:step-up:"), eq("8:3"), eq(Duration.ofMinutes(5)));
        when(values.getAndDelete(anyString())).thenReturn("8:3", (String) null);
        service.exigirEConsumir(3L, usuario.getEmail(), resposta.token());
        assertThatThrownBy(() -> service.exigirEConsumir(3L, usuario.getEmail(), resposta.token()))
                .hasMessageContaining("inválida ou expirada");
    }

    @Test void nuncaCriaTokenComSenhaInvalida() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        Usuario usuario = new Usuario(); usuario.setSenha("hash");
        when(usuarios.findByEmail("u@e.test")).thenReturn(Optional.of(usuario));
        var service = new FiscalStepUpService(usuarios, encoder, redis, mock(FiscalAuthorizationService.class));
        assertThatThrownBy(() -> service.confirmar(3L, "u@e.test", "errada")).hasMessage("Credencial inválida.");
        verifyNoInteractions(redis);
    }
}
