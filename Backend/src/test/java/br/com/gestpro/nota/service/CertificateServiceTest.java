package br.com.gestpro.nota.service;

import br.com.gestpro.nota.model.CertificadoDigital;
import br.com.gestpro.nota.repository.CertificadoDigitalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CertificateServiceTest {
    @Test void recusaPfxAcimaDoLimiteAntesDeProcessarOuPersistir() {
        CertificadoDigitalRepository repository = mock(CertificadoDigitalRepository.class);
        CertificateService service = new CertificateService(repository, mock(FiscalEncryptionService.class));

        assertThatThrownBy(() -> service.salvar(1L, new byte[CertificateService.MAX_PFX_BYTES + 1], "senha"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("1 MiB");
        verifyNoInteractions(repository);
    }

    @Test void recusaSenhaAusenteOuExcessiva() {
        CertificateService service = new CertificateService(mock(CertificadoDigitalRepository.class),
                mock(FiscalEncryptionService.class));
        assertThatThrownBy(() -> service.salvar(1L, new byte[]{1}, ""))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Senha");
        assertThatThrownBy(() -> service.salvar(1L, new byte[]{1}, "x".repeat(513)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Senha");
    }

    @Test void informaVencimentoProximoSemExporConteudoCifrado() {
        CertificadoDigital certificado = mock(CertificadoDigital.class);
        when(certificado.getTitular()).thenReturn("CN=Empresa");
        when(certificado.getEmissor()).thenReturn("CN=AC Teste");
        when(certificado.getNumeroSerie()).thenReturn("123");
        when(certificado.getValidoDe()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(certificado.getValidoAte()).thenReturn(Instant.now().plus(10, ChronoUnit.DAYS));
        CertificadoDigitalRepository repository = mock(CertificadoDigitalRepository.class);
        when(repository.findByEmpresaId(1L)).thenReturn(Optional.of(certificado));

        var resumo = new CertificateService(repository, mock(FiscalEncryptionService.class)).consultar(1L);

        assertThat(resumo).containsEntry("expiraEmBreve", "true").containsEntry("expirado", "false");
        assertThat(resumo).doesNotContainKeys("arquivo", "senha", "arquivoCifrado", "senhaCifrada");
    }

    @Test void excluiCertificadoEApagaBuffersDaEntidade() {
        byte[] arquivo = {1, 2, 3}; byte[] arquivoNonce = {4, 5};
        byte[] senha = {6, 7}; byte[] senhaNonce = {8, 9};
        CertificadoDigital certificado = mock(CertificadoDigital.class);
        when(certificado.getArquivoCifrado()).thenReturn(arquivo);
        when(certificado.getArquivoNonce()).thenReturn(arquivoNonce);
        when(certificado.getSenhaCifrada()).thenReturn(senha);
        when(certificado.getSenhaNonce()).thenReturn(senhaNonce);
        CertificadoDigitalRepository repository = mock(CertificadoDigitalRepository.class);
        when(repository.findByEmpresaId(1L)).thenReturn(Optional.of(certificado));

        new CertificateService(repository, mock(FiscalEncryptionService.class)).excluir(1L);

        verify(repository).delete(certificado); verify(repository).flush();
        assertThat(arquivo).containsOnly(0); assertThat(arquivoNonce).containsOnly(0);
        assertThat(senha).containsOnly(0); assertThat(senhaNonce).containsOnly(0);
    }
}
