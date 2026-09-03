package br.com.gestpro.nota.dto;
import br.com.gestpro.nota.model.FiscalDelivery;
public record FiscalDeliveryResponse(Long id, String canal, String status, String destinatarioMascarado) {
    public static FiscalDeliveryResponse from(FiscalDelivery d, String mascarado) {
        return new FiscalDeliveryResponse(d.getId(), d.getCanal().name(), d.getStatus().name(), mascarado);
    }
}
