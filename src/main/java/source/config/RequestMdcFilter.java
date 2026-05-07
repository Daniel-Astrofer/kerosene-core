package source.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Populates MDC (Mapped Diagnostic Context) for every incoming HTTP request.
 *
 * Fields added (all safe — no PII):
 * - requestId : random UUID per request for log correlation
 * - method : HTTP verb (GET, POST, …)
 * - path : request URI (no query string, no body)
 * - userId : authenticated user ID (Long) — NOT username/passphrase
 * - service : coarse-grained service layer derived from the path prefix
 *
 * All MDC keys are cleared after the response is sent to avoid context leakage
 * across thread-pool reuse.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestMdcFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_METHOD = "method";
    private static final String MDC_PATH = "path";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_SERVICE = "service";
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // --- Correlation ID ---
            String requestId = resolveInboundId(request.getHeader("X-Request-Id"));
            String correlationId = resolveInboundId(request.getHeader("X-Correlation-Id"));
            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_CORRELATION_ID, correlationId != null ? correlationId : requestId);
            response.setHeader("X-Request-Id", requestId);
            response.setHeader("X-Correlation-Id", correlationId != null ? correlationId : requestId);

            // --- Request metadata (non-sensitive) ---
            MDC.put(MDC_METHOD, request.getMethod());
            MDC.put(MDC_PATH, sanitizePath(request.getServletPath()));

            // --- Authenticated user ID (numeric, NOT username) ---
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long) {
                MDC.put(MDC_USER_ID, auth.getPrincipal().toString());
            } else if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                try {
                    MDC.put(MDC_USER_ID, auth.getName());
                } catch (Exception ignored) {
                    // Non-numeric principal — skip
                }
            }

            // --- Coarse-grained service label ---
            MDC.put(MDC_SERVICE, resolveService(request.getServletPath()));

            filterChain.doFilter(request, response);

        } finally {
            // Always clean up — critical for thread-pool safety
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_SERVICE);
        }
    }

    private String resolveInboundId(String candidate) {
        if (candidate != null) {
            String trimmed = candidate.trim();
            if (SAFE_ID_PATTERN.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Strip query strings and matrix params from the path.
     * Only keeps the URI path itself to avoid leaking user data in URL params.
     */
    private String sanitizePath(String path) {
        if (path == null)
            return "unknown";
        int idx = path.indexOf('?');
        return idx >= 0 ? path.substring(0, idx) : path;
    }

    /**
     * Maps a URL prefix to a coarse-grained service label for log grouping.
     */
    private String resolveService(String path) {
        if (path == null)
            return "unknown";
        if (path.startsWith("/auth"))
            return "auth";
        if (path.startsWith("/ledger"))
            return "ledger";
        if (path.startsWith("/transactions"))
            return "transactions";
        if (path.startsWith("/wallet"))
            return "wallet";
        if (path.startsWith("/voucher"))
            return "voucher";
        if (path.startsWith("/ws"))
            return "websocket";
        return "general";
    }
}
