package br.com.gestpro.nota.service;

import java.util.Base64;

final class TestFiscalKeys {
    private TestFiscalKeys() {}
    static String ephemeralKey() { return Base64.getEncoder().encodeToString(new byte[32]); }
}
