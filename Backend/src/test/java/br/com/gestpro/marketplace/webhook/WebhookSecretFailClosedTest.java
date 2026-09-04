package br.com.gestpro.marketplace.webhook;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.marketplace.client.MercadoLivreApiClient;
import br.com.gestpro.marketplace.client.ShopeeApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookSecretFailClosedTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shopeeRecusaSegredoPadraoConhecido() throws Exception {
        WebhookShopeeService service = new WebhookShopeeService(
                mock(WebhookProcessorService.class), mock(ShopeeApiClient.class));
        ReflectionTestUtils.setField(service, "shopeePartnerKey", "CONFIGURE_ME");
        ReflectionTestUtils.setField(service, "webhookUrl", "https://api.example.com/api/v1/webhooks/shopee");

        assertThatThrownBy(() -> service.processar("{}".getBytes(), "qualquer-assinatura", mapper.readTree("{}")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void mercadoLivreRecusaSegredoPadraoConhecido() throws Exception {
        WebhookMercadoLivreService service = new WebhookMercadoLivreService(
                mock(WebhookProcessorService.class), mock(MercadoLivreApiClient.class));
        ReflectionTestUtils.setField(service, "mlSecretKey", "CONFIGURE_ME");

        assertThatThrownBy(() -> service.processar("ts=1,v1=falso", "request", mapper.readTree(
                        "{\"topic\":\"orders_v2\",\"resource\":\"/orders/1\"}")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
