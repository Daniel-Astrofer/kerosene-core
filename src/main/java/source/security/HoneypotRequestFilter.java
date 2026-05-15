package source.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ─── HONEYPOT DEFENSE FILTER ──────────────────────────────────────────────────
 *
 * Protects public auth endpoints from automated bots and scanners.
 *
 * <p>Strategy: A decoy field ({@code __hp}) is declared in the public request DTO
 * schema. Legitimate frontend clients are instructed to NEVER send this field.
 * Automated attack tools that enumerate JSON fields will populate it, revealing
 * their non-human origin.
 *
 * <p>When triggered:
 * <ul>
 *   <li>The request is silently blackholed with a fake HTTP 200 OK response.</li>
 *   <li>The internal system state is left completely untouched.</li>
 *   <li>The attacker receives no useful feedback or error code.</li>
 * </ul>
 *
 * <p>Applied only to: {@code POST /auth/login}, {@code POST /auth/signup},
 * {@code POST /auth/signup/totp/verify}, {@code POST /auth/login/totp/verify}.
 */
@Component
public class HoneypotRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HoneypotRequestFilter.class);

    /** The hidden honeypot JSON field name — must stay in sync with UserDTO. */
    private static final String HONEYPOT_FIELD = "__hp";

    private final ObjectMapper objectMapper;

    public HoneypotRequestFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        // Only inspect POST requests on auth registration/login paths
        return !("POST".equalsIgnoreCase(method) && (
                path.startsWith("/auth/login") ||
                path.startsWith("/auth/signup")));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Wrap the request so we can read the body twice (filter + controller)
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        try {
            byte[] body = wrappedRequest.getCachedBody();
            if (body != null && body.length > 0) {
                JsonNode node = objectMapper.readTree(body);
                JsonNode hp = node.get(HONEYPOT_FIELD);
                if (hp != null && !hp.isNull() && hp.asText("").length() > 0) {
                    // Honeypot triggered — blackhole the request silently
                    log.warn("[HONEYPOT] Triggered from IP={} path={} UA={}",
                            request.getRemoteAddr(),
                            request.getRequestURI(),
                            request.getHeader("User-Agent"));
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/json");
                    response.getWriter()
                            .write("{\"success\":true,\"message\":\"OK\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
                    return; // Do not proceed to controller
                }
            }
        } catch (Exception e) {
            log.warn("[HONEYPOT] Malformed JSON rejected on path={}: {}", request.getRequestURI(), e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter()
                    .write("{\"success\":false,\"message\":\"Malformed JSON payload.\",\"timestamp\":\""
                            + java.time.Instant.now() + "\"}");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }
}
