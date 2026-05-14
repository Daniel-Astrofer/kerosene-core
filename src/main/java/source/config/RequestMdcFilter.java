package source.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

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
@Order(1)
public class RequestMdcFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_METHOD = "method";
    private static final String MDC_PATH = "path";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_SERVICE = "service";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // --- Correlation ID ---
            MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString().replace("-", "").substring(0, 12));

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
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_SERVICE);
        }
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
