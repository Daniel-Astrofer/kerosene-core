package source.auth.application.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.security.CachedBodyHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> BODY_BUCKET_FIELDS = List.of(
            "username",
            "sessionId",
            "credentialId",
            "passkeyCredentialId",
            "userHandle",
            "txid");

    private final RedisServicer redisService;
    private final ObjectMapper objectMapper;
    private static final int MAX_REQUESTS_PER_MINUTE = 100; // General limit
    private static final int PUBLIC_MAX_REQUESTS_PER_MINUTE = 20; // Stricter for public endpoints

    public RateLimitFilter(RedisServicer redisService, ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpServletRequest effectiveRequest = request;
        if (shouldCacheRequestBody(request)) {
            effectiveRequest = new CachedBodyHttpServletRequest(request);
        }

        String uri = effectiveRequest.getRequestURI();
        String requesterBucket = resolveRequesterBucket(effectiveRequest);

        long currentMinute = System.currentTimeMillis() / 60000;
        String key = "ratelimit:" + uriBucket(uri) + ":" + requesterBucket + ":" + currentMinute;

        int limit = isPublicRateLimitedRoute(uri) ? PUBLIC_MAX_REQUESTS_PER_MINUTE : MAX_REQUESTS_PER_MINUTE;

        Long requests = redisService.increment(key);
        if (requests == 1) {
            redisService.expire(key, 60);
        }

        if (requests > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests");
            return;
        }

        filterChain.doFilter(effectiveRequest, response);
    }

    private boolean shouldCacheRequestBody(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
            return false;
        }

        String contentType = request.getContentType();
        return contentType != null && contentType.startsWith("application/json");
    }

    private String resolveRequesterBucket(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (hasText(authorization)) {
            return "authz:" + stableHash(authorization);
        }

        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        if (hasText(idempotencyKey)) {
            return "idem:" + stableHash(idempotencyKey);
        }

        String digest = request.getHeader("Digest");
        if (hasText(digest)) {
            return "digest:" + stableHash(digest);
        }

        if (request instanceof CachedBodyHttpServletRequest cachedRequest) {
            String bodyIdentity = extractBodyIdentity(cachedRequest.getCachedBody());
            if (hasText(bodyIdentity)) {
                return bodyIdentity;
            }
        }

        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
        return "net:" + stableHash(remoteAddr + "|" + request.getMethod() + "|" + request.getRequestURI());
    }

    private String extractBodyIdentity(byte[] cachedBody) {
        if (cachedBody == null || cachedBody.length == 0) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(cachedBody);
            for (String field : BODY_BUCKET_FIELDS) {
                String value = root.path(field).asText(null);
                if (hasText(value)) {
                    return field + ":" + stableHash(value.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException ignored) {
            // Fall back to the raw payload hash when the body is not parseable JSON.
        }

        return "body:" + stableHash(new String(cachedBody, StandardCharsets.UTF_8));
    }

    private String uriBucket(String uri) {
        if (!hasText(uri)) {
            return "unknown";
        }
        if (uri.startsWith("/auth/") || "/auth".equals(uri)) {
            return "auth";
        }
        if (uri.startsWith("/voucher/")) {
            return "voucher";
        }
        if (uri.startsWith("/ledger/")) {
            return "ledger";
        }
        return "general";
    }

    private boolean isPublicRateLimitedRoute(String uri) {
        return uri != null && (uri.startsWith("/auth/") || "/auth".equals(uri) || uri.startsWith("/voucher/"));
    }

    private String stableHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
