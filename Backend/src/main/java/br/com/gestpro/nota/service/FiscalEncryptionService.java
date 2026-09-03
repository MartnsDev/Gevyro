package br.com.gestpro.nota.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class FiscalEncryptionService {
    private static final int NONCE_BYTES = 12;
    private final SecureRandom random = new SecureRandom();
    private final String encodedKey;
    private SecretKeySpec key;

    public FiscalEncryptionService(@Value("${FISCAL_MASTER_KEY}") String encodedKey) { this.encodedKey = encodedKey; }

    @PostConstruct
    void validateKey() {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("FISCAL_MASTER_KEY deve estar em Base64.", e); }
        if (decoded.length != 32) throw new IllegalStateException("FISCAL_MASTER_KEY deve conter exatamente 32 bytes.");
        key = new SecretKeySpec(decoded, "AES");
    }

    public Encrypted encrypt(byte[] plain) {
        byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
        return new Encrypted(crypt(Cipher.ENCRYPT_MODE, plain, nonce), nonce);
    }
    public byte[] decrypt(byte[] cipherText, byte[] nonce) {
        if (nonce == null || nonce.length != NONCE_BYTES) throw new IllegalArgumentException("Nonce fiscal inválido.");
        return crypt(Cipher.DECRYPT_MODE, cipherText, nonce);
    }
    /** Índice determinístico autenticado para deduplicação sem hash de dado pessoal em claro. */
    public String blindIndex(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao indexar dado fiscal protegido.", e);
        }
    }
    private byte[] crypt(int mode, byte[] input, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Falha ao proteger segredo fiscal.", e); }
    }
    public record Encrypted(byte[] cipherText, byte[] nonce) {}
}
