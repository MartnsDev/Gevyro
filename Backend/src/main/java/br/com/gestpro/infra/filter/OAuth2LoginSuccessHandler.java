package br.com.gestpro.infra.filter;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.service.AuthCookieService;
import br.com.gestpro.auth.service.LoginGoogleOperation;
import br.com.gestpro.plano.StatusAcesso;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final LoginGoogleOperation loginGoogleOperation;
    private final AuthCookieService authCookieService;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(
            LoginGoogleOperation loginGoogleOperation,
            AuthCookieService authCookieService,
            @Value("${app.frontend.url}") String frontendUrl
    ) {
        this.loginGoogleOperation = loginGoogleOperation;
        this.authCookieService = authCookieService;
        this.frontendUrl = normalizarFrontendUrl(frontendUrl);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        try {
            if (!(authentication
                    instanceof OAuth2AuthenticationToken oauth)) {
                throw new IllegalStateException(
                        "Autenticação OAuth2 inválida."
                );
            }

            Map<String, Object> attributes =
                    oauth.getPrincipal().getAttributes();

            String email = atributoObrigatorio(
                    attributes,
                    "email"
            ).trim().toLowerCase(Locale.ROOT);

            validarEmailConfirmado(attributes);

            String nome = atributoOpcional(
                    attributes,
                    "name"
            );

            if (nome == null || nome.isBlank()) {
                nome = email.substring(
                        0,
                        email.indexOf('@')
                );
            }

            String foto = atributoOpcional(
                    attributes,
                    "picture"
            );

            Usuario usuario =
                    loginGoogleOperation.execute(
                            email,
                            nome,
                            foto
                    );

            String token =
                    loginGoogleOperation
                            .gerarToken(usuario);

            /*
             * Substitui qualquer cookie de uma conta anterior.
             */
            authCookieService.adicionar(
                    response,
                    token
            );

            /*
             * Remove a sessão temporária usada para state/nonce do OAuth.
             * A autenticação seguinte será feita exclusivamente pelo JWT.
             */
            HttpSession session =
                    request.getSession(false);

            if (session != null) {
                session.invalidate();
            }
            authCookieService.removerSessaoTemporaria(response);

            response.setHeader(
                    "Cache-Control",
                    "no-store, no-cache, must-revalidate"
            );

            response.setHeader(
                    "Pragma",
                    "no-cache"
            );

            String destino =
                    usuario.getStatusAcesso()
                            == StatusAcesso.ATIVO
                            ? frontendUrl + "/dashboard"
                            : frontendUrl + "/pagamento";

            response.sendRedirect(destino);
        } catch (Exception exception) {
            log.error(
                    "Falha ao concluir login Google.",
                    exception
            );

            authCookieService.remover(response);
            limparSessaoTemporaria(request, response);

            response.sendRedirect(
                    frontendUrl
                            + "/auth/login?error=oauth2"
            );
        }
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        log.warn("Google recusou ou não concluiu a autenticação.");
        authCookieService.remover(response);
        limparSessaoTemporaria(request, response);
        response.sendRedirect(frontendUrl + "/auth/login?error=oauth2");
    }

    private void limparSessaoTemporaria(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        authCookieService.removerSessaoTemporaria(response);
    }

    private void validarEmailConfirmado(
            Map<String, Object> attributes
    ) {
        Object valor =
                attributes.get("email_verified");

        /*
         * Dependendo do provider/SDK, pode chegar como Boolean ou String.
         */
        boolean confirmado =
                Boolean.TRUE.equals(valor)
                        || "true".equalsIgnoreCase(
                        String.valueOf(valor)
                );

        if (!confirmado) {
            throw new IllegalStateException(
                    "Google não confirmou o e-mail."
            );
        }
    }

    private String atributoObrigatorio(
            Map<String, Object> attributes,
            String nome
    ) {
        Object valor = attributes.get(nome);

        if (valor == null
                || String.valueOf(valor).isBlank()
                || "null".equalsIgnoreCase(
                String.valueOf(valor)
        )) {
            throw new IllegalStateException(
                    "Atributo OAuth2 ausente: " + nome
            );
        }

        return String.valueOf(valor);
    }

    private String atributoOpcional(
            Map<String, Object> attributes,
            String nome
    ) {
        Object valor = attributes.get(nome);

        if (valor == null
                || "null".equalsIgnoreCase(
                String.valueOf(valor)
        )) {
            return null;
        }

        String texto =
                String.valueOf(valor).trim();

        return texto.isBlank() ? null : texto;
    }

    private static String normalizarFrontendUrl(
            String frontendUrl
    ) {
        if (frontendUrl == null
                || frontendUrl.isBlank()) {
            throw new IllegalStateException(
                    "app.frontend.url não configurada."
            );
        }

        String url = frontendUrl.trim();

        if (!url.startsWith("https://")
                && !url.startsWith("http://localhost")) {
            throw new IllegalStateException(
                    "app.frontend.url precisa usar HTTPS."
            );
        }

        return url.endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }
}
