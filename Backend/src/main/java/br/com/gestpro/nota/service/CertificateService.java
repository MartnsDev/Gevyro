package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.model.CertificadoDigital;
import br.com.gestpro.nota.repository.CertificadoDigitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class CertificateService {
    public static final int MAX_PFX_BYTES = 1024 * 1024;
    static final int MAX_PASSWORD_CHARS = 512;
    private final CertificadoDigitalRepository repository;
    private final FiscalEncryptionService encryption;

    @Transactional
    public Map<String, String> salvar(Long empresaId, byte[] pfx, String senha) {
        if (empresaId == null || pfx == null || pfx.length == 0 || pfx.length > MAX_PFX_BYTES)
            throw new ApiException("Certificado A1 deve possuir no máximo 1 MiB.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/certificado");
        if (senha == null || senha.isEmpty() || senha.length() > MAX_PASSWORD_CHARS)
            throw new ApiException("Senha do certificado ausente ou excessiva.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/certificado");
        X509Certificate certificate = validar(pfx, senha);
        var arquivo = encryption.encrypt(pfx);
        var segredo = encryption.encrypt(senha.getBytes(StandardCharsets.UTF_8));
        CertificadoDigital entity = repository.findByEmpresaId(empresaId).orElseGet(() -> new CertificadoDigital(empresaId));
        entity.substituir(arquivo.cipherText(), arquivo.nonce(), segredo.cipherText(), segredo.nonce(),
                certificate.getSubjectX500Principal().getName(), certificate.getIssuerX500Principal().getName(),
                certificate.getSerialNumber().toString(), certificate.getNotBefore().toInstant(), certificate.getNotAfter().toInstant());
        repository.save(entity);
        return resumo(entity);
    }

    @Transactional(readOnly = true)
    public Material carregar(Long empresaId) {
        CertificadoDigital entity = repository.findByEmpresaId(empresaId).orElseThrow(() ->
                new ApiException("Certificado digital A1 não configurado.", HttpStatus.PRECONDITION_FAILED, "/api/nota-fiscal"));
        byte[] arquivo = encryption.decrypt(entity.getArquivoCifrado(), entity.getArquivoNonce());
        byte[] senhaBytes = encryption.decrypt(entity.getSenhaCifrada(), entity.getSenhaNonce());
        String senha = new String(senhaBytes, StandardCharsets.UTF_8);
        Arrays.fill(senhaBytes, (byte) 0);
        return new Material(arquivo, senha);
    }

    @Transactional(readOnly = true)
    public Map<String, String> consultar(Long empresaId) {
        return repository.findByEmpresaId(empresaId).map(this::resumo).orElse(Map.of());
    }

    private X509Certificate validar(byte[] pfx, String senha) {
        char[] segredo = senha.toCharArray();
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (var input = new ByteArrayInputStream(pfx)) { keyStore.load(input, segredo); }
            List<String> chaves = new ArrayList<>();
            for (String candidato : Collections.list(keyStore.aliases()))
                if (keyStore.isKeyEntry(candidato)) chaves.add(candidato);
            if (chaves.size() != 1) throw new IllegalArgumentException("PKCS#12 deve possuir uma única chave privada.");
            String alias = chaves.get(0);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            certificate.checkValidity();
            if (keyStore.getKey(alias, segredo) == null) throw new IllegalArgumentException("Certificado sem chave privada.");
            return certificate;
        } catch (Exception e) {
            throw new ApiException("Certificado inválido, expirado ou com senha incorreta.", HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/certificado");
        } finally { Arrays.fill(segredo, '\0'); }
    }

    private Map<String, String> resumo(CertificadoDigital entity) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("titular", entity.getTitular()); result.put("emissor", entity.getEmissor());
        result.put("serialNumber", entity.getNumeroSerie()); result.put("validoDe", entity.getValidoDe().toString());
        result.put("validoAte", entity.getValidoAte().toString()); result.put("configurado", "true");
        long dias = Math.max(0, Duration.between(Instant.now(), entity.getValidoAte()).toDays());
        result.put("diasParaExpirar", Long.toString(dias));
        result.put("expiraEmBreve", Boolean.toString(dias <= 30));
        result.put("expirado", Boolean.toString(entity.getValidoAte().isBefore(Instant.now())));
        return result;
    }

    public record Material(byte[] arquivo, String senha) implements AutoCloseable {
        @Override public void close() { Arrays.fill(arquivo, (byte) 0); }
    }
}
