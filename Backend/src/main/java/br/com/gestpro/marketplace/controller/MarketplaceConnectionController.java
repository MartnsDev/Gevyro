package br.com.gestpro.marketplace.controller;

import br.com.gestpro.marketplace.model.MarketplaceConnection;
import br.com.gestpro.marketplace.model.MarketplaceProductLink;
import br.com.gestpro.marketplace.service.MarketplaceConnectionService;
import br.com.gestpro.marketplace.service.OAuthStateService;
import br.com.gestpro.pedidos.CanalVenda;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
public class MarketplaceConnectionController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceConnectionController.class);

    private final MarketplaceConnectionService service;
    private final OAuthStateService oauthStateService;

    @Value("${app.frontend.url}")
    private String urlFrontend;

    @Value("${app.base-url}")
    private String urlApi;


    /**
     * Shopee OAuth callback.
     * painel: https://minha-api.com/api/v1/marketplace/callback/shopee
     * state opaco, temporário e de uso único, criado pelo backend.
     */
    @GetMapping("/callback/shopee")
    public void callbackShopee(
            @RequestParam String code,
            @RequestParam(required = false) String shop_id,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {

        try {
            OAuthStateService.DadosState dados = oauthStateService.consumir(state);
            service.processarCallbackShopee(dados.empresaId(), dados.emailUsuario(), code, shop_id);
            response.sendRedirect(urlFrontend + "/dashboard/pedidos?sucesso=true");
        } catch (Exception e) {
            log.error("Erro no callback Shopee: {}", e.getMessage(), e);
            response.sendRedirect(urlFrontend + "/dashboard/pedidos?erro=integracao_falhou");
        }
    }

    /**
     * Mercado Livre OAuth callback.
     * https://minhaAPI.com/api/v1/marketplace/callback/mercadolivre
     * state opaco, temporário e de uso único, criado pelo backend.
     */
    @GetMapping("/callback/mercadolivre")
    public void callbackMercadoLivre(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {

        try {
            OAuthStateService.DadosState dados = oauthStateService.consumir(state);
            String redirectUri = urlApi + "/api/v1/marketplace/callback/mercadolivre";
            service.processarCallbackMercadoLivre(dados.empresaId(), dados.emailUsuario(), code, redirectUri);
            response.sendRedirect(urlFrontend + "/dashboard/pedidos?sucesso=true");
        } catch (Exception e) {
            log.error("Erro no callback Mercado Livre: {}", e.getMessage(), e);
            response.sendRedirect(urlFrontend + "/dashboard/pedidos?erro=integracao_falhou");
        }
    }


    @PostMapping("/empresa/{empresaId}/conectar")
    public ResponseEntity<MarketplaceConnection> conectar(
            @PathVariable Long empresaId,
            @Valid @RequestBody ConectarRequest req,
            Authentication auth) {

        MarketplaceConnection conn = service.conectar(
                empresaId, auth.getName(),
                req.getMarketplace(), req.getSellerId(),
                req.getAccessToken(), req.getRefreshToken(),
                req.getExpiresInSeconds());

        return ResponseEntity.status(HttpStatus.CREATED).body(conn);
    }

    @DeleteMapping("/empresa/{empresaId}/desconectar")
    public ResponseEntity<Void> desconectar(
            @PathVariable Long empresaId,
            @RequestParam CanalVenda marketplace,
            Authentication auth) {

        service.desconectar(empresaId, auth.getName(), marketplace);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/empresa/{empresaId}/conexoes")
    public ResponseEntity<List<MarketplaceConnection>> listarConexoes(
            @PathVariable Long empresaId,
            Authentication auth) {

        return ResponseEntity.ok(service.listarConexoes(empresaId, auth.getName()));
    }

    @GetMapping("/empresa/{empresaId}/oauth-state")
    public ResponseEntity<OAuthStateResponse> criarOAuthState(
            @PathVariable Long empresaId, Authentication auth) {
        service.validarAcessoEmpresa(empresaId, auth.getName());
        return ResponseEntity.ok(new OAuthStateResponse(oauthStateService.criar(empresaId, auth.getName())));
    }


    @PostMapping("/empresa/{empresaId}/vinculos")
    public ResponseEntity<MarketplaceProductLink> vincular(
            @PathVariable Long empresaId,
            @Valid @RequestBody VincularRequest req,
            Authentication auth) {

        MarketplaceProductLink link = service.vincularProduto(
                empresaId, auth.getName(),
                req.getProdutoId(), req.getMarketplace(),
                req.getAnuncioId(), req.getAnuncioTitulo());

        return ResponseEntity.status(HttpStatus.CREATED).body(link);
    }

    @GetMapping("/empresa/{empresaId}/vinculos")
    public ResponseEntity<List<MarketplaceProductLink>> listarVinculos(
            @PathVariable Long empresaId,
            @RequestParam CanalVenda marketplace,
            Authentication auth) {

        return ResponseEntity.ok(service.listarVinculos(empresaId, auth.getName(), marketplace));
    }

    @DeleteMapping("/empresa/{empresaId}/vinculos/{linkId}")
    public ResponseEntity<Void> removerVinculo(
            @PathVariable Long empresaId,
            @PathVariable Long linkId,
            Authentication auth) {

        service.removerVinculo(empresaId, auth.getName(), linkId);
        return ResponseEntity.noContent().build();
    }


    public record OAuthStateResponse(String state) {}


    @Getter @Setter
    public static class ConectarRequest {
        @NotNull  private CanalVenda marketplace;
        @NotBlank private String sellerId;
        @NotBlank private String accessToken;
        private String refreshToken;
        private Long expiresInSeconds;
    }

    @Getter @Setter
    public static class VincularRequest {
        @NotNull  private Long produtoId;
        @NotNull  private CanalVenda marketplace;
        @NotBlank private String anuncioId;
        private String anuncioTitulo;
    }
}
