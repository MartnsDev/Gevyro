package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.dto.FiscalWebhookConfigRequest;
import br.com.gestpro.nota.model.FiscalWebhookConfig;
import br.com.gestpro.nota.repository.FiscalWebhookConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiscalWebhookConfigServiceTest {
    private FiscalWebhookConfigRepository repository;
    private FiscalEncryptionService encryption;
    private FiscalWebhookConfigService service;

    @BeforeEach void setup() {
        repository = mock(FiscalWebhookConfigRepository.class);
        encryption = new FiscalEncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
        encryption.validateKey();
        service = new FiscalWebhookConfigService(repository, mock(FiscalAuthorizationService.class),
                encryption, mock(FiscalAuditService.class));
        ReflectionTestUtils.setField(service, "allowedHosts", "hooks.example.com");
        ReflectionTestUtils.setField(service, "dispatchEnabled", false);
        when(repository.findByEmpresaId(3L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void aceitaSomenteHttpsEmHostAprovadoECifraUrlESegredo() {
        var request = new FiscalWebhookConfigRequest("https://hooks.example.com/gevyro/fiscal", "s".repeat(32),
                Set.of(FiscalWebhookConfigRequest.Evento.DOCUMENTO_AUTORIZADO), false);
        var response = service.salvar(3L, request, "ator");
        ArgumentCaptor<FiscalWebhookConfig> captor = ArgumentCaptor.forClass(FiscalWebhookConfig.class);
        verify(repository).save(captor.capture());
        FiscalWebhookConfig config = captor.getValue();
        assertThat(response.hostAprovado()).isEqualTo("hooks.example.com");
        assertThat(response.ativo()).isFalse();
        assertThat(new String(config.getUrlCifrada(), StandardCharsets.UTF_8)).doesNotContain("hooks.example.com");
        assertThat(new String(encryption.decrypt(config.getUrlCifrada(), config.getUrlNonce()), StandardCharsets.UTF_8))
                .isEqualTo("https://hooks.example.com/gevyro/fiscal");
    }

    @Test void bloqueiaAtivacaoSemLiberacaoDoAmbiente() {
        var request = new FiscalWebhookConfigRequest("https://hooks.example.com/fiscal", "s".repeat(32),
                Set.of(FiscalWebhookConfigRequest.Evento.DOCUMENTO_AUTORIZADO), true);
        assertThatThrownBy(() -> service.salvar(3L, request, "ator"))
                .isInstanceOf(ApiException.class).hasMessageContaining("não foi liberado");
    }

    @Test void bloqueiaVetoresComunsDeSsrf() {
        assertThatThrownBy(() -> service.validarUrl("http://hooks.example.com/fiscal")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validarUrl("https://127.0.0.1/fiscal")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validarUrl("https://user@hooks.example.com/fiscal")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validarUrl("https://hooks.example.com:8443/fiscal")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validarUrl("https://nao-aprovado.example/fiscal")).isInstanceOf(ApiException.class);
    }
}
