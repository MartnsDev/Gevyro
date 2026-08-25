package br.com.gestpro.marketplace.service;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.marketplace.model.MarketplaceConnection;
import br.com.gestpro.marketplace.model.MarketplaceProductLink;
import br.com.gestpro.marketplace.repository.MarketplaceConnectionRepository;
import br.com.gestpro.marketplace.repository.MarketplaceProductLinkRepository;
import br.com.gestpro.pedidos.CanalVenda;
import br.com.gestpro.produto.model.Produto;
import br.com.gestpro.produto.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MarketplaceConnectionService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceConnectionService.class);
    private static final String PATH = "/api/v1/marketplace";
    private static final Set<CanalVenda> SUPORTADOS = Set.of(CanalVenda.SHOPEE, CanalVenda.MERCADO_LIVRE);

    private final MarketplaceConnectionRepository connectionRepository;
    private final MarketplaceProductLinkRepository linkRepository;
    private final EmpresaRepository empresaRepository;
    private final ProdutoRepository produtoRepository;
    private final WebClient.Builder webClientBuilder;

    // CORREÇÃO: unificado para o mesmo namespace usado em ShopeeApiClient e
    // WebhookShopeeService (gestpro.marketplace.shopee.*). Antes, esta classe
    // usava "shopee.partner.id" / "shopee.partner.key" -- uma chave diferente
    // das outras duas classes -- então bastava configurar uma variável para
    // deixar a outra vazia silenciosamente.
    @Value("${gestpro.marketplace.shopee.partner-id:0}")
    private String shopeePartnerId;

    @Value("${gestpro.marketplace.shopee.partner-key:}")
    private String shopeePartnerKey;

    @Value("${gestpro.marketplace.mercadolivre.client-id:}")
    private String mlClientId;

    @Value("${gestpro.marketplace.mercadolivre.client-secret:}")
    private String mlClientSecret;


    @Transactional
    public MarketplaceConnection conectar(
            Long empresaId,
            String emailUsuario,
            CanalVenda marketplace,
            String sellerId,
            String accessToken,
            String refreshToken,
            Long expiresInSeconds) {

        validarMarketplaceSuportado(marketplace);
        Empresa empresa = buscarEmpresaComPermissao(empresaId, emailUsuario);

        MarketplaceConnection conn = connectionRepository
                .findByEmpresaIdAndMarketplaceAndActiveTrue(empresaId, marketplace)
                .orElseGet(() -> {
                    MarketplaceConnection nova = new MarketplaceConnection();
                    nova.setEmpresa(empresa);
                    nova.setMarketplace(marketplace);
                    return nova;
                });

        conn.setSellerId(sellerId);
        conn.setAccessToken(accessToken);
        conn.setRefreshToken(refreshToken);
        conn.setTokenExpiresAt(expiresInSeconds != null
                ? LocalDateTime.now().plusSeconds(expiresInSeconds)
                : null);
        conn.setActive(true);

        MarketplaceConnection salva = connectionRepository.save(conn);
        log.info("Empresa {} conectou marketplace={} sellerId={}", empresa.getNomeFantasia(), marketplace, sellerId);
        return salva;
    }

    @Transactional
    public void desconectar(Long empresaId, String emailUsuario, CanalVenda marketplace) {
        validarMarketplaceSuportado(marketplace);
        buscarEmpresaComPermissao(empresaId, emailUsuario);

        MarketplaceConnection conn = connectionRepository
                .findByEmpresaIdAndMarketplaceAndActiveTrue(empresaId, marketplace)
                .orElseThrow(() -> new ApiException(
                        "Nenhuma conexão ativa com " + marketplace.name(), HttpStatus.NOT_FOUND, PATH));

        conn.setActive(false);
        connectionRepository.save(conn);
        log.info("Empresa {} desconectou marketplace={}", empresaId, marketplace);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceConnection> listarConexoes(Long empresaId, String emailUsuario) {
        buscarEmpresaComPermissao(empresaId, emailUsuario);
        return connectionRepository.findByEmpresaIdAndActiveTrue(empresaId);
    }


    @Transactional
    public MarketplaceProductLink vincularProduto(
            Long empresaId,
            String emailUsuario,
            Long produtoId,
            CanalVenda marketplace,
            String anuncioId,
            String anuncioTitulo) {

        validarMarketplaceSuportado(marketplace);
        buscarEmpresaComPermissao(empresaId, emailUsuario);

        connectionRepository
                .findByEmpresaIdAndMarketplaceAndActiveTrue(empresaId, marketplace)
                .orElseThrow(() -> new ApiException(
                        "Conecte o marketplace " + marketplace.name() + " antes de vincular produtos.",
                        HttpStatus.UNPROCESSABLE_ENTITY, PATH));

        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new ApiException("Produto não encontrado", HttpStatus.NOT_FOUND, PATH));

        if (!produto.getEmpresa().getId().equals(empresaId))
            throw new ApiException("Produto não pertence a esta empresa.", HttpStatus.FORBIDDEN, PATH);

        MarketplaceProductLink link = linkRepository
                .findByMarketplaceAndAnuncioId(marketplace, anuncioId)
                .orElseGet(MarketplaceProductLink::new);

        link.setProduto(produto);
        link.setMarketplace(marketplace);
        link.setAnuncioId(anuncioId);
        link.setAnuncioTitulo(anuncioTitulo);

        MarketplaceProductLink salvo = linkRepository.save(link);
        log.info("Vínculo criado: marketplace={} anuncio={} → produto={}", marketplace, anuncioId, produto.getNome());
        return salvo;
    }

    @Transactional
    public void removerVinculo(Long empresaId, String emailUsuario, Long linkId) {
        buscarEmpresaComPermissao(empresaId, emailUsuario);
        MarketplaceProductLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ApiException("Vínculo não encontrado", HttpStatus.NOT_FOUND, PATH));
        if (!link.getProduto().getEmpresa().getId().equals(empresaId))
            throw new ApiException("Sem permissão para este vínculo.", HttpStatus.FORBIDDEN, PATH);
        linkRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceProductLink> listarVinculos(Long empresaId, String emailUsuario, CanalVenda marketplace) {
        buscarEmpresaComPermissao(empresaId, emailUsuario);
        return linkRepository.findByMarketplaceAndProduto_Empresa_Id(marketplace, empresaId);
    }


    /**
     * Shopee: troca o authorization code pelo access_token via HMAC auth.
     * Documentação: https://open.shopee.com/documents?module=87&type=2&id=58
     */
    @Transactional
    public void processarCallbackShopee(Long empresaId, String emailUsuario, String code, String shopId) {
        buscarEmpresaComPermissao(empresaId, emailUsuario);
        log.info("Processando callback Shopee: empresaId={} shopId={}", empresaId, shopId);

        // CORREÇÃO: shopId vem de @RequestParam(required = false) no controller,
        // então pode ser null -- Long.parseLong(null) explodia com NPE confuso.
        if (shopId == null || shopId.isBlank()) {
            throw new ApiException("shop_id ausente no retorno da Shopee.", HttpStatus.BAD_REQUEST, PATH);
        }

        Map<String, Object> body = Map.of(
                "code", code,
                "shop_id", Long.parseLong(shopId),
                "partner_id", Long.parseLong(shopeePartnerId)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resposta = webClientBuilder.build()
                .post()
                .uri("https://partner.shopeemobile.com/api/v2/auth/token/get")
                .header("Authorization", "Bearer " + shopeePartnerKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // A Shopee retorna a chave error vazia quando a operação funciona.
        String erro = resposta != null ? String.valueOf(resposta.get("error")) : null;
        boolean falhou = resposta == null || (erro != null && !erro.isBlank() && !erro.equals("null"));

        if (falhou) {
            String mensagem = resposta != null ? String.valueOf(resposta.get("message")) : "resposta nula";
            log.error("Shopee retornou erro no callback: {}", mensagem);
            throw new ApiException("Falha ao obter token Shopee: " + mensagem, HttpStatus.BAD_GATEWAY, PATH);
        }

        String accessToken  = String.valueOf(resposta.get("access_token"));
        String refreshToken = String.valueOf(resposta.get("refresh_token"));
        Long   expiresIn    = resposta.get("expire_in") != null
                ? ((Number) resposta.get("expire_in")).longValue() : null;

        salvarConexao(empresaId, CanalVenda.SHOPEE, shopId, accessToken, refreshToken, expiresIn);
        log.info("Conexão Shopee salva: empresaId={} shopId={}", empresaId, shopId);
    }

    /**
     * Mercado Livre: troca o authorization code pelo access_token via OAuth 2.0 padrão.
     * Documentação: https://developers.mercadolivre.com.br/pt_br/autenticacao-e-autorizacao
     *
     * @param redirectUri deve ser idêntica à cadastrada no painel do app ML.
     */
    @Transactional
    public void processarCallbackMercadoLivre(Long empresaId, String emailUsuario, String code, String redirectUri) {
        buscarEmpresaComPermissao(empresaId, emailUsuario);
        log.info("Processando callback Mercado Livre: empresaId={}", empresaId);

        @SuppressWarnings("unchecked")
        Map<String, Object> resposta = webClientBuilder.build()
                .post()
                .uri("https://api.mercadolibre.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=authorization_code"
                        + "&client_id=" + mlClientId
                        + "&client_secret=" + mlClientSecret
                        + "&code=" + code
                        + "&redirect_uri=" + redirectUri)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resposta == null || resposta.containsKey("error")) {
            String erro = resposta != null ? String.valueOf(resposta.get("message")) : "resposta nula";
            log.error("Mercado Livre retornou erro no callback: {}", erro);
            throw new ApiException("Falha ao obter token Mercado Livre: " + erro, HttpStatus.BAD_GATEWAY, PATH);
        }

        String accessToken  = String.valueOf(resposta.get("access_token"));
        String refreshToken = String.valueOf(resposta.get("refresh_token"));
        String sellerId     = String.valueOf(resposta.get("user_id"));
        Long   expiresIn    = resposta.get("expires_in") != null
                ? ((Number) resposta.get("expires_in")).longValue() : null;

        salvarConexao(empresaId, CanalVenda.MERCADO_LIVRE, sellerId, accessToken, refreshToken, expiresIn);
        log.info("Conexão Mercado Livre salva: empresaId={} sellerId={}", empresaId, sellerId);
    }


    private void salvarConexao(Long empresaId, CanalVenda marketplace, String sellerId,
                               String accessToken, String refreshToken, Long expiresIn) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada", HttpStatus.NOT_FOUND, PATH));

        MarketplaceConnection conn = connectionRepository
                .findByEmpresaIdAndMarketplaceAndActiveTrue(empresaId, marketplace)
                .orElseGet(() -> {
                    MarketplaceConnection nova = new MarketplaceConnection();
                    nova.setEmpresa(empresa);
                    nova.setMarketplace(marketplace);
                    return nova;
                });

        conn.setSellerId(sellerId);
        conn.setAccessToken(accessToken);
        conn.setRefreshToken(refreshToken);
        conn.setTokenExpiresAt(expiresIn != null ? LocalDateTime.now().plusSeconds(expiresIn) : null);
        conn.setActive(true);

        connectionRepository.save(conn);
    }

    private Empresa buscarEmpresaComPermissao(Long empresaId, String emailUsuario) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada", HttpStatus.NOT_FOUND, PATH));
        if (!empresa.getDono().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão para esta empresa.", HttpStatus.FORBIDDEN, PATH);
        return empresa;
    }

    @Transactional(readOnly = true)
    public void validarAcessoEmpresa(Long empresaId, String emailUsuario) {
        buscarEmpresaComPermissao(empresaId, emailUsuario);
    }

    private void validarMarketplaceSuportado(CanalVenda marketplace) {
        if (!SUPORTADOS.contains(marketplace))
            throw new ApiException(
                    "Marketplace não suportado para integração: " + marketplace,
                    HttpStatus.BAD_REQUEST, PATH);
    }
}
