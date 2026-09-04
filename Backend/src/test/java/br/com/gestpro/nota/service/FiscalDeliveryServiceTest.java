package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.model.FiscalDelivery;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.FiscalDeliveryRepository;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiscalDeliveryServiceTest {
    private NotaFiscalRepository notas;
    private FiscalDeliveryRepository deliveries;
    private FiscalAuthorizationService authorization;
    private FiscalEncryptionService encryption;
    private FiscalDeliveryService service;

    @BeforeEach void setup() {
        notas = mock(NotaFiscalRepository.class);
        deliveries = mock(FiscalDeliveryRepository.class);
        authorization = mock(FiscalAuthorizationService.class);
        encryption = new FiscalEncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
        encryption.validateKey();
        service = new FiscalDeliveryService(notas, deliveries, authorization, encryption);
        ReflectionTestUtils.setField(service, "emailEnabled", false);
        when(deliveries.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void cifraNormalizaMascaraEDesativaDespachoPorPadrao() {
        NotaFiscal nota = NotaFiscal.builder().id(7L).empresaId(3L).status(NotaFiscalStatus.AUTORIZADA).build();
        when(notas.findById(7L)).thenReturn(Optional.of(nota));
        when(deliveries.findByDedupKey(anyString())).thenReturn(Optional.empty());

        var resposta = service.solicitarEmail(7L, " Fiscal@Exemplo.COM ", "ator@example.com");

        ArgumentCaptor<FiscalDelivery> captor = ArgumentCaptor.forClass(FiscalDelivery.class);
        verify(deliveries).save(captor.capture());
        FiscalDelivery delivery = captor.getValue();
        assertThat(delivery.getStatus()).isEqualTo(FiscalDelivery.Status.AGUARDANDO_CONFIGURACAO);
        assertThat(resposta.destinatarioMascarado()).isEqualTo("f***@exemplo.com");
        assertThat(new String(delivery.getDestinatarioCifrado(), StandardCharsets.UTF_8))
                .doesNotContain("fiscal", "exemplo.com");
        assertThat(new String(encryption.decrypt(delivery.getDestinatarioCifrado(), delivery.getDestinatarioNonce()),
                StandardCharsets.UTF_8)).isEqualTo("fiscal@exemplo.com");
    }

    @Test void rejeitaHeaderInjection() {
        NotaFiscal nota = NotaFiscal.builder().id(7L).empresaId(3L).status(NotaFiscalStatus.AUTORIZADA).build();
        when(notas.findById(7L)).thenReturn(Optional.of(nota));
        assertThatThrownBy(() -> service.solicitarEmail(7L, "a@b.com\r\nBcc:x@y.com", "ator"))
                .isInstanceOf(ApiException.class).hasMessageContaining("inválido");
        verify(deliveries, never()).save(any());
    }

    @Test void rejeitaDocumentoNaoAutorizado() {
        NotaFiscal nota = NotaFiscal.builder().id(7L).empresaId(3L).status(NotaFiscalStatus.DIGITACAO).build();
        when(notas.findById(7L)).thenReturn(Optional.of(nota));
        assertThatThrownBy(() -> service.solicitarEmail(7L, "a@b.com", "ator"))
                .isInstanceOf(ApiException.class).hasMessageContaining("autorizado");
    }

    @Test void mesmaSolicitacaoRetornaOutboxExistente() {
        NotaFiscal nota = NotaFiscal.builder().id(7L).empresaId(3L).status(NotaFiscalStatus.AUTORIZADA).build();
        when(notas.findById(7L)).thenReturn(Optional.of(nota));
        byte[] encrypted = encryption.encrypt("a@b.com".getBytes(StandardCharsets.UTF_8)).cipherText();
        FiscalDelivery existente = new FiscalDelivery(3L, 7L, encrypted, new byte[12], "x", false);
        when(deliveries.findByDedupKey(anyString())).thenReturn(Optional.of(existente));
        service.solicitarEmail(7L, "a@b.com", "ator");
        verify(deliveries, never()).save(any());
    }

    @Test void acessoCruzadoParaAntesDePersistirEntrega() {
        NotaFiscal nota = NotaFiscal.builder().id(7L).empresaId(3L).status(NotaFiscalStatus.AUTORIZADA).build();
        when(notas.findById(7L)).thenReturn(Optional.of(nota));
        doThrow(new ApiException("Sem permissão para esta operação fiscal.",
                org.springframework.http.HttpStatus.FORBIDDEN, "/api/fiscal"))
                .when(authorization).exigir(3L, "intruso@example.com", br.com.gestpro.nota.FiscalPermission.EXPORTAR);
        assertThatThrownBy(() -> service.solicitarEmail(7L, "a@b.com", "intruso@example.com"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(deliveries, never()).findByDedupKey(anyString());
        verify(deliveries, never()).save(any());
    }
}
