package br.com.gestpro.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String received = request.getHeader(HEADER);
        String correlationId = received != null && received.matches("[A-Za-z0-9._:-]{8,100}")
                ? received : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try { chain.doFilter(request, response); }
        finally { MDC.remove(MDC_KEY); }
    }
}
