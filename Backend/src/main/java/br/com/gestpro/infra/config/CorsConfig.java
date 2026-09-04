package br.com.gestpro.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.net.URI;

@Configuration
public class CorsConfig {

    private final String frontendUrl;
    private final String allowedOrigins;
    private final boolean production;

    public CorsConfig(
            @Value("${app.frontend.url}")
            String frontendUrl,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            Environment environment
    ) {
        this.production = environment.acceptsProfiles(Profiles.of("prod"));
        this.frontendUrl = normalizarOrigem(frontendUrl);
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource
    corsConfigurationSource() {

        CorsConfiguration config =
                new CorsConfiguration();

        config.setAllowCredentials(true);

        Set<String> origens =
                new LinkedHashSet<>();

        origens.add(frontendUrl);
        for (String origem : allowedOrigins.split(",")) {
            if (!origem.isBlank()) origens.add(normalizarOrigem(origem));
        }
        config.setAllowedOrigins(
                new ArrayList<>(origens)
        );

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Content-Type",
                "X-CSRF-TOKEN",
                "X-Requested-With",
                "Idempotency-Key",
                "X-Correlation-ID",
                "Accept"
        ));

        config.setExposedHeaders(List.of("X-Correlation-ID", "Retry-After"));

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                config
        );

        return source;
    }

    private String normalizarOrigem(String origem) {
        if (origem == null || origem.isBlank()) {
            throw new IllegalStateException(
                    "app.frontend.url não configurada."
            );
        }

        String valor = origem.trim();

        while (valor.endsWith("/")) {
            valor = valor.substring(
                    0,
                    valor.length() - 1
            );
        }

        if (valor.contains("*")) throw new IllegalStateException("CORS não aceita origem curinga.");
        try {
            URI uri = URI.create(valor);
            boolean esquemaValido = "https".equalsIgnoreCase(uri.getScheme())
                    || (!production && "http".equalsIgnoreCase(uri.getScheme()));
            if (!esquemaValido || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPath() != null && !uri.getPath().isEmpty()
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("Origem CORS inválida: informe somente esquema, host e porta.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Origem CORS inválida.", ex);
        }
        return valor;
    }
}
