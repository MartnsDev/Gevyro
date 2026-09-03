package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.*;
import br.com.gestpro.nota.dto.FiscalDeliveryResponse;
import br.com.gestpro.nota.model.*;
import br.com.gestpro.nota.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class FiscalDeliveryService {
    private final NotaFiscalRepository notas;
    private final FiscalDeliveryRepository deliveries;
    private final FiscalAuthorizationService authorization;
    private final FiscalEncryptionService encryption;
    @Value("${fiscal.delivery.email.enabled:false}") private boolean emailEnabled;

    @Transactional
    public FiscalDeliveryResponse solicitarEmail(Long documentoId, String destinatario, String ator) {
        NotaFiscal nota = notas.findById(documentoId).orElseThrow(() -> new ApiException(
                "Documento fiscal não encontrado.", HttpStatus.NOT_FOUND, "/api/nota-fiscal/entregas/email"));
        authorization.exigir(nota.getEmpresaId(), ator, FiscalPermission.EXPORTAR);
        if (nota.getStatus() != NotaFiscalStatus.AUTORIZADA && nota.getStatus() != NotaFiscalStatus.CANCELADA)
            throw new ApiException("Somente documento autorizado ou cancelado pode ser enviado por e-mail.",
                    HttpStatus.CONFLICT, "/api/nota-fiscal/entregas/email");
        String email = normalizar(destinatario);
        byte[] dedupPlain = (nota.getEmpresaId() + ":" + documentoId + ":EMAIL:" + email)
                .getBytes(StandardCharsets.UTF_8);
        String dedup;
        try { dedup = encryption.blindIndex(dedupPlain); }
        finally { Arrays.fill(dedupPlain, (byte) 0); }
        FiscalDelivery existente = deliveries.findByDedupKey(dedup).orElse(null);
        if (existente != null) return FiscalDeliveryResponse.from(existente, mascarar(email));
        byte[] plain = email.getBytes(StandardCharsets.UTF_8);
        try {
            var encrypted = encryption.encrypt(plain);
            FiscalDelivery delivery = deliveries.save(new FiscalDelivery(nota.getEmpresaId(), documentoId,
                    encrypted.cipherText(), encrypted.nonce(), dedup, emailEnabled));
            return FiscalDeliveryResponse.from(delivery, mascarar(email));
        } finally { Arrays.fill(plain, (byte) 0); }
    }

    private String normalizar(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || email.indexOf('\r') >= 0 || email.indexOf('\n') >= 0
                || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new ApiException("Endereço de e-mail inválido.", HttpStatus.BAD_REQUEST,
                    "/api/nota-fiscal/entregas/email");
        return email;
    }
    private String mascarar(String email) {
        int at = email.indexOf('@'); return email.substring(0, 1) + "***@" + email.substring(at + 1);
    }
}
