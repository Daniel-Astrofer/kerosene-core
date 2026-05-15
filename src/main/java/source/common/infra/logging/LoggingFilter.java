package source.common.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger accessLogger = LoggerFactory.getLogger("source.common.infra.logging.LoggingFilter");

    private static final Pattern SENSITIVE_KEYS_PATTERN = Pattern.compile(
            "\"(password|passphrase|private_key|mnemonic|secret|totp|totpSecret)\"\\s*:\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap the request and response to cache their content for logging
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logAccessAndMask(requestWrapper, responseWrapper, duration);
            // Must copy body to the actual response
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logAccessAndMask(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
            long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        // Optional logic to skip noisy endpoints like healthchecks
        if (uri.startsWith("/actuator") || uri.startsWith("/ws")) {
            return;
        }

        Map<String, String> safeHeaders = getSafeHeaders(request);
        String safeRequestBody = maskSensitivePayload(
                getPayload(request.getContentAsByteArray(), request.getCharacterEncoding()));
        String safeResponseBody = maskSensitivePayload(
                getPayload(response.getContentAsByteArray(), response.getCharacterEncoding()));

        // OpSec rule: IP is not logged correctly or is masked pseudo-anonymously
        String clientIp = "MASKED_IP";

        accessLogger.info("HTTP {} {} - Status: {} - Duration: {}ms - IP: {} - Headers: {} - ReqBody: {} - ResBody: {}",
                method, uri, status, duration, clientIp, safeHeaders, safeRequestBody, safeResponseBody);
    }

    private Map<String, String> getSafeHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);

                if ("authorization".equalsIgnoreCase(headerName) ||
                        "x-forwarded-for".equalsIgnoreCase(headerName) ||
                        "cookie".equalsIgnoreCase(headerName)) {
                    headers.put(headerName, "***MASKED***");
                } else {
                    headers.put(headerName, headerValue);
                }
            }
        }
        return headers;
    }

    private String getPayload(byte[] buf, String characterEncoding) {
        if (buf == null || buf.length == 0)
            return "";
        try {
            int length = Math.min(buf.length, 5120); // truncate up to 5KB to avoid memory blowup
            return new String(buf, 0, length, characterEncoding != null ? characterEncoding : "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            return "[Unsupported Encoding]";
        }
    }

    private String maskSensitivePayload(String payload) {
        if (payload == null || payload.isEmpty())
            return payload;
        // Replaces the actual value with ***MASKED***
        return SENSITIVE_KEYS_PATTERN.matcher(payload).replaceAll("\"$1\":\"***MASKED***\"");
    }
}
