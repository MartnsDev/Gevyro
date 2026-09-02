package br.com.gestpro.nota.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Transporte mTLS da SEFIN Nacional, sem logs de XML, certificado ou senha. */
@Component
public class NfseNationalHttpClient {
    private static final int MAX_RESPONSE = 4 * 1024 * 1024;
    private final ObjectMapper json;
    private final boolean producaoHabilitada;

    public NfseNationalHttpClient(ObjectMapper json,
            @Value("${fiscal.nfse.producao-habilitada:false}") boolean producaoHabilitada) {
        this.json = json;
        this.producaoHabilitada = producaoHabilitada;
    }

    public Resposta emitir(URI base, boolean homologacao, String dpsAssinada,
                           byte[] certificado, String senhaCertificado) {
        if (!homologacao && !producaoHabilitada)
            throw new IllegalStateException("Transmissão NFS-e em produção não está habilitada.");
        if (base == null || !"https".equalsIgnoreCase(base.getScheme()) || certificado == null
                || certificado.length == 0 || senhaCertificado == null || dpsAssinada == null)
            throw new IllegalArgumentException("Dados incompletos para transmissão segura da DPS.");
        try {
            String payload = json.createObjectNode().put("dpsXmlGZipB64", gzipBase64(dpsAssinada)).toString();
            HttpRequest request = HttpRequest.newBuilder(base.resolve(base.getPath().endsWith("/") ? "nfse" : base.getPath() + "/nfse"))
                    .timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .sslContext(sslContext(certificado, senhaCertificado)).build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream in = response.body()) { body = lerLimitado(in, MAX_RESPONSE); }
            JsonNode resposta = body.length == 0 ? json.createObjectNode() : json.readTree(body);
            String xmlCompactado = texto(resposta, "nfseXmlGZipB64");
            String xml = xmlCompactado == null ? null : gunzipBase64(xmlCompactado);
            return new Resposta(response.statusCode(), texto(resposta, "chaveAcesso"), xml,
                    primeiroTexto(resposta, "codigo", "tipoAmbiente"), primeiroTexto(resposta, "mensagem", "erro", "descricao"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new FiscalProviderException("Falha segura na comunicação mTLS com a SEFIN Nacional.", true, e);
        }
    }

    private SSLContext sslContext(byte[] pfx, String senha) throws Exception {
        char[] segredo = senha.toCharArray();
        try {
            KeyStore chave = KeyStore.getInstance("PKCS12");
            try (InputStream in = new ByteArrayInputStream(pfx)) { chave.load(in, segredo); }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(chave, segredo);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return context;
        } finally { Arrays.fill(segredo, '\0'); }
    }

    private String gzipBase64(String xml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) { gzip.write(xml.getBytes(StandardCharsets.UTF_8)); }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private String gunzipBase64(String valor) throws IOException {
        byte[] comprimido;
        try { comprimido = Base64.getDecoder().decode(valor); }
        catch (IllegalArgumentException e) { throw new IOException("Resposta Base64 inválida.", e); }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido))) {
            return new String(lerLimitado(gzip, MAX_RESPONSE), StandardCharsets.UTF_8);
        }
    }

    private byte[] lerLimitado(InputStream in, int limite) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int total = 0, lidos;
        while ((lidos = in.read(buffer)) != -1) {
            total += lidos; if (total > limite) throw new IOException("Resposta fiscal excede o limite seguro.");
            out.write(buffer, 0, lidos);
        }
        return out.toByteArray();
    }

    private String texto(JsonNode node, String campo) {
        JsonNode valor = node.get(campo); return valor == null || valor.isNull() ? null : valor.asText();
    }
    private String primeiroTexto(JsonNode node, String... campos) {
        for (String campo : campos) { String valor = texto(node, campo); if (valor != null && !valor.isBlank()) return valor; }
        return null;
    }

    public record Resposta(int httpStatus, String chaveAcesso, String nfseXml, String codigo, String motivo) {}
}
