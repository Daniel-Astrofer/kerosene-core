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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LogContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_KEY = "userId";
    private static final String ENDPOINT_KEY = "endpoint";
    private static final String NETWORK_TYPE_KEY = "networkType";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Micrometer Tracing automaticamente injeta traceId/spanId no MDC.
            // Aqui adicionamos carimbos específicos de negócio do Kerosene.

            String userId = "anonymous"; // Integrar com AuthService no futuro

            String networkType = resolveNetworkType(request);

            MDC.put(USER_ID_KEY, userId);
            MDC.put(ENDPOINT_KEY, request.getRequestURI());
            MDC.put(NETWORK_TYPE_KEY, networkType);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_KEY);
            MDC.remove(ENDPOINT_KEY);
            MDC.remove(NETWORK_TYPE_KEY);
        }
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
