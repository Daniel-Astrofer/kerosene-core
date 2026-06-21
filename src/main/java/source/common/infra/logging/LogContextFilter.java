package source.common.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LogContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_KEY = "userId";
    private static final String ENDPOINT_KEY = "endpoint";
    private static final String NETWORK_TYPE_KEY = "networkType";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:/@-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Micrometer Tracing automaticamente injeta traceId/spanId no MDC.
            // Aqui adicionamos carimbos específicos de negócio do Kerosene.

            String userId = "anonymous"; // Integrar com AuthService no futuro

            String networkType = resolveNetworkType(request);
            String correlationId = resolveCorrelationId(request);

            MDC.put(USER_ID_KEY, userId);
            MDC.put(ENDPOINT_KEY, request.getRequestURI());
            MDC.put(NETWORK_TYPE_KEY, networkType);
            MDC.put(StructuredLogField.CORRELATION_ID, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_KEY);
            MDC.remove(ENDPOINT_KEY);
            MDC.remove(NETWORK_TYPE_KEY);
            MDC.remove(StructuredLogField.CORRELATION_ID);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = safeHeaderValue(request.getHeader(CORRELATION_ID_HEADER));
        if (correlationId != null) {
            return correlationId;
        }
        String requestId = safeHeaderValue(request.getHeader(REQUEST_ID_HEADER));
        if (requestId != null) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private String safeHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CORRELATION_ID_LENGTH) {
            return null;
        }
        return SAFE_CORRELATION_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    private String resolveNetworkType(HttpServletRequest request) {
        String serverName = request.getServerName();
        if (serverName != null && serverName.endsWith(".onion")) {
            return "TOR";
        }
        String header = request.getHeader("X-Network-Type");
        if ("TOR".equalsIgnoreCase(header)) {
            return "TOR";
        }
        return "CLEARNET";
    }
}
