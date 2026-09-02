package br.com.gestpro.nota.service;

import br.com.gestpro.nota.config.NotaFiscalConfig;
import br.com.gestpro.nota.model.FiscalAuditLog;
import br.com.gestpro.nota.repository.FiscalAuditStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FiscalAuditServiceTest {
    @AfterEach void clearMdc() { MDC.clear(); }

    @Test void encadeiaRegistroAoHashAnteriorEPropagaCorrelationId() {
        FiscalAuditStore store = mock(FiscalAuditStore.class);
        NotaFiscalConfig.NotaFiscalProperties properties = new NotaFiscalConfig.NotaFiscalProperties();
        properties.setHomologacao(true);
        FiscalAuditLog anterior = new FiscalAuditLog(7L, 1L, "ANTERIOR", "ator", "HOMOLOGACAO",
                "SUCESSO", null, "corr-old", "0".repeat(64), "a".repeat(64), Instant.now());
        when(store.ultimo(7L)).thenReturn(Optional.of(anterior));
        MDC.put("correlationId", "corr-12345678");

        new FiscalAuditService(store, properties, new FiscalSensitiveDataRedactor())
                .registrar(7L, 9L, "XML_BAIXADO", "user@example.com", "SUCESSO", null);

        ArgumentCaptor<FiscalAuditLog> captor = ArgumentCaptor.forClass(FiscalAuditLog.class);
        verify(store).bloquearEmpresa(7L);
        verify(store).adicionar(captor.capture());
        assertThat(captor.getValue().getHashAnterior()).isEqualTo("a".repeat(64));
        assertThat(captor.getValue().getHashRegistro()).hasSize(64).isNotEqualTo(captor.getValue().getHashAnterior());
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-12345678");
        assertThat(captor.getValue().getAmbiente()).isEqualTo("HOMOLOGACAO");
    }

    @Test void removeDadosSensiveisDosDetalhesSemAlterarIdentidadeDoAtor() {
        FiscalAuditStore store = mock(FiscalAuditStore.class);
        NotaFiscalConfig.NotaFiscalProperties properties = new NotaFiscalConfig.NotaFiscalProperties();
        when(store.ultimo(7L)).thenReturn(Optional.empty());

        new FiscalAuditService(store, properties, new FiscalSensitiveDataRedactor()).registrar(
                7L, 9L, "FALHA", "responsavel@example.com", "ERRO",
                "cliente=cliente@example.com cpf=123.456.789-09 token=nao-deve-vazar");

        ArgumentCaptor<FiscalAuditLog> captor = ArgumentCaptor.forClass(FiscalAuditLog.class);
        verify(store).adicionar(captor.capture());
        assertThat(captor.getValue().getAtor()).isEqualTo("responsavel@example.com");
        assertThat(captor.getValue().getDetalhes())
                .contains("[EMAIL_REDACTED]", "[DOCUMENTO_REDACTED]", "[SEGREDO_REDACTED]")
                .doesNotContain("cliente@example.com", "123.456.789-09", "nao-deve-vazar");
    }
}
