package br.com.gestpro.infra.security;

import br.com.gestpro.auth.service.CookieSecurityProperties;
import br.com.gestpro.infra.filter.JwtAuthenticationFilter;
import br.com.gestpro.infra.filter.OAuth2LoginSuccessHandler;
import br.com.gestpro.infra.filter.PlanAccessFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PlanAccessFilter planAccessFilter;
    private final OAuth2LoginSuccessHandler oauthSuccessHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CookieSecurityProperties cookieSecurityProperties;

    public SecurityConfig(
            CustomOAuth2UserService customOAuth2UserService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            PlanAccessFilter planAccessFilter,
            OAuth2LoginSuccessHandler oauthSuccessHandler,
            CorsConfigurationSource corsConfigurationSource,
            CookieSecurityProperties cookieSecurityProperties
    ) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.planAccessFilter = planAccessFilter;
        this.oauthSuccessHandler = oauthSuccessHandler;
        this.corsConfigurationSource = corsConfigurationSource;
        this.cookieSecurityProperties = cookieSecurityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        CookieCsrfTokenRepository csrfRepository =
                new CookieCsrfTokenRepository();

        csrfRepository.setHeaderName("X-CSRF-TOKEN");
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .httpOnly(true)
                .secure(cookieSecurityProperties.isSecure())
                .sameSite(cookieSecurityProperties.getSameSite())
                .path("/")
        );

        http
                .headers(headers -> headers
                        .cacheControl(Customizer.withDefaults()))

                .cors(cors -> cors
                        .configurationSource(corsConfigurationSource))

                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers(
                                "/api/payments/webhook",
                                "/api/v1/webhooks/**"
                        ))

                /*
                 * OAuth2 usa uma sessão temporária para guardar state/nonce.
                 * A autenticação da API continua sendo feita pelo JWT.
                 */
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        ))

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"erro\":\"NAO_AUTENTICADO\"}"
                            );
                        }))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        .requestMatchers(
                                "/auth/login",
                                "/auth/cadastro",
                                "/auth/confirmar",
                                "/auth/reenviar-confirmacao",
                                "/auth/logout",
                                "/auth/csrf",
                                "/api/auth/esqueceu-senha",
                                "/api/auth/redefinir-senha",
                                "/api/payments/webhook",
                                "/api/v1/webhooks/**",
                                "/api/v1/marketplace/callback/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/favicon.ico"
                        )
                        .permitAll()

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/api-docs/**"
                        )
                        .permitAll()

                        /*
                         * Checkout e session-info exigem autenticação, inclusive
                         * quando o plano está inativo.
                         */
                        .requestMatchers("/api/payments/**")
                        .authenticated()

                        .anyRequest()
                        .authenticated()
                )

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(oauthSuccessHandler)
                        .failureHandler(oauthSuccessHandler));

        http.addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        http.addFilterAfter(
                planAccessFilter,
                JwtAuthenticationFilter.class
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
