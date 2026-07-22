package source.common.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * HTTP access log filter — emits one structured JSON event per request.
 *
 * <p>All events are tagged with {@link LogDomain#ACCESS} so they route to
 * the dedicated ACCESS appender and can be tailed independently.
 *
 * <p>What is logged (no PII, no bodies):
 * <ul>
 *   <li>{@code http.method}, {@code url.path}, {@code http.status_code}</li>
 *   <li>{@code duration_ms} — wall-clock time for the full filter chain</li>
 *   <li>{@code req.bytes}, {@code res.bytes} — content lengths</li>
 *   <li>{@code client.ip} → always {@code MASKED_IP}</li>
 * </ul>
 *
 * <p>Paths skipped: {@code /actuator}, {@code /health/}, {@code /ws} (WebSocket upgrade).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper req  = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();

        try {
            chain.doFilter(req, res);
        } finally {
            logAccess(req, res, System.currentTimeMillis() - start);
            res.copyBodyToResponse();
        }
    }

    private void logAccess(ContentCachingRequestWrapper req,
                           ContentCachingResponseWrapper res,
                           long durationMs) {
        String uri = req.getRequestURI();

        if (uri.startsWith("/actuator") || uri.startsWith("/health/") || uri.startsWith("/ws")) {
            return;
        }

        log.info(LogDomain.ACCESS, "http_access {} {} {} {} {} {} {} {} {} {}",
                kv(StructuredLogField.EVENT, "http.access"),
                kv(StructuredLogField.DOMAIN, "access"),
                kv(StructuredLogField.OPERATION, "http.request"),
                kv(StructuredLogField.HTTP_METHOD, req.getMethod()),
                kv(StructuredLogField.URL_PATH, uri),
                kv(StructuredLogField.HTTP_STATUS_CODE, res.getStatus()),
                kv(StructuredLogField.DURATION_MS, durationMs),
                kv(StructuredLogField.CLIENT_IP, LogSanitizer.maskedIp(req.getRemoteAddr())),
                kv(StructuredLogField.REQUEST_BYTES, req.getContentAsByteArray().length),
                kv(StructuredLogField.RESPONSE_BYTES, res.getContentAsByteArray().length));
    }
}
