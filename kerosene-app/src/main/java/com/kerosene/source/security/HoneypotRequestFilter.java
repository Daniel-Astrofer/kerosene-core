package source.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import source.security.application.honeypot.HoneypotInspectionCommand;
import source.security.application.honeypot.HoneypotInspectionUseCase;
import source.security.domain.honeypot.HoneypotInspectionResult;
import source.security.infra.honeypot.HoneypotHttpResponseWriter;
import source.security.infra.honeypot.JacksonRequestJsonBodyParser;

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

    private final HoneypotInspectionUseCase inspectionUseCase;
    private final HoneypotHttpResponseWriter responseWriter;

    @Autowired
    public HoneypotRequestFilter(
            HoneypotInspectionUseCase inspectionUseCase,
            HoneypotHttpResponseWriter responseWriter) {
        this.inspectionUseCase = inspectionUseCase;
        this.responseWriter = responseWriter;
    }

    HoneypotRequestFilter(ObjectMapper objectMapper) {
        this(
                new HoneypotInspectionUseCase(new JacksonRequestJsonBodyParser(objectMapper)),
                new HoneypotHttpResponseWriter());
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

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        HoneypotInspectionResult inspectionResult = inspectionUseCase.inspect(new HoneypotInspectionCommand(
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                wrappedRequest.getCachedBody()));

        if (!inspectionResult.shouldContinueFilterChain()) {
            responseWriter.write(inspectionResult, response);
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }
}
