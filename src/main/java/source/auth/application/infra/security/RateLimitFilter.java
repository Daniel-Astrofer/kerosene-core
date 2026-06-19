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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> BODY_BUCKET_FIELDS = List.of(
            "username",
            "sessionId",
            "recoverySessionId",
            "credentialId",
            "passkeyCredentialId",
            "userHandle",
            "idempotencyKey",
            "fromWalletName",
            "payerWalletName",
            "txid");
    private static final List<String> QUERY_BUCKET_FIELDS = List.of(
            "username",
            "sessionId",
            "recoverySessionId",
            "linkId",
            "txid");
    private static final List<String> PUBLIC_AUTH_IDENTITY_ROUTES = List.of(
            "/auth/admin/login",
            "/auth/device-key/challenge",
            "/auth/device-key/onboarding",
            "/auth/device-key/verify",
            "/auth/login",
            "/auth/passkey/challenge",
            "/auth/passkey/onboarding",
            "/auth/passkey/verify",
            "/auth/pow/challenge",
            "/auth/recovery/emergency",
            "/auth/signup");
    private static final List<RouteLimit> ROUTE_LIMITS = List.of(
            new RouteLimit("/auth/recovery/emergency", "auth-recovery", 5),
            new RouteLimit("/auth/passkey/verify", "auth-passkey-verify", 10),
            new RouteLimit("/auth/passkey/register", "auth-passkey-register", 8),
            new RouteLimit("/auth/passkey/onboarding/finish", "auth-passkey-onboarding-finish", 8),
            new RouteLimit("/auth/passkey/onboarding/start", "auth-passkey-onboarding-start", 12),
            new RouteLimit("/auth/passkey/challenge", "auth-passkey-challenge", 20),
            new RouteLimit("/auth/login", "auth-login", 10),
            new RouteLimit("/auth/signup", "auth-signup", 8),
            new RouteLimit("/auth/pow/challenge", "auth-pow-challenge", 30),
            new RouteLimit("/kfe/transactions/quote", "kfe-transaction-quote", 20),
            new RouteLimit("/kfe/transactions", "kfe-transaction-submit", 6),
            new RouteLimit("/kfe/wallets", "kfe-wallets", 20),
            new RouteLimit("/kfe/users/", "kfe-receiving-capabilities", 20));

    private final RedisServicer redisService;
    private final ObjectMapper objectMapper;
    private static final int MAX_REQUESTS_PER_MINUTE = 100; // General limit
    private static final int PUBLIC_MAX_REQUESTS_PER_MINUTE = 20; // Stricter for public endpoints

    public RateLimitFilter(RedisServicer redisService, ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.startsWith("/health/")
                || uri.startsWith("/actuator/health")
                || uri.startsWith("/quorum/"));
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
        RouteLimit routeLimit = resolveRouteLimit(uri)
                .orElseGet(() -> new RouteLimit(uriBucket(uri), uriBucket(uri), defaultLimitFor(uri)));

        long currentMinute = System.currentTimeMillis() / 60000;
        String key = "ratelimit:" + routeLimit.bucket() + ":" + requesterBucket + ":" + currentMinute;
        int limit = routeLimit.requestsPerMinute();

        Long requests = redisService.increment(key);
        if (requests == 1) {
            redisService.expire(key, 60);
        }

        if (requests > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"success":false,"message":"Too many requests","errorCode":"RATE_LIMITED"}
                    """);
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
        boolean preferAuthorization = shouldPreferAuthorization(request.getRequestURI());
        if (preferAuthorization) {
            String authorizationBucket = authorizationBucket(request);
            if (hasText(authorizationBucket)) {
                return authorizationBucket;
            }
        }

        String queryIdentity = extractQueryIdentity(request);
        if (hasText(queryIdentity)) {
            return queryIdentity;
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

    private boolean shouldPreferAuthorization(String uri) {
        if (!hasText(uri)) {
            return true;
        }
        return PUBLIC_AUTH_IDENTITY_ROUTES.stream()
                .noneMatch(publicRoute -> uri.equals(publicRoute) || uri.startsWith(publicRoute + "/"));
    }

    private String authorizationBucket(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!hasText(authorization)) {
            return null;
        }
        return "authz:" + stableHash(authorization.trim());
    }

    private String extractQueryIdentity(HttpServletRequest request) {
        for (String field : QUERY_BUCKET_FIELDS) {
            String value = request.getParameter(field);
            if (hasText(value)) {
                return "query-" + field + ":" + stableHash(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return null;
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
            return null;
        }

        return null;
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
        if (uri.startsWith("/kfe/")) {
            return "kfe";
        }
        return "general";
    }

    private Optional<RouteLimit> resolveRouteLimit(String uri) {
        if (!hasText(uri)) {
            return Optional.empty();
        }
        return ROUTE_LIMITS.stream()
                .filter(route -> uri.equals(route.pathPrefix()) || uri.startsWith(route.pathPrefix()))
                .max(Comparator.comparingInt(route -> route.pathPrefix().length()));
    }

    private int defaultLimitFor(String uri) {
        return uri != null && (uri.startsWith("/auth/") || "/auth".equals(uri))
                ? PUBLIC_MAX_REQUESTS_PER_MINUTE
                : MAX_REQUESTS_PER_MINUTE;
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

    private record RouteLimit(String pathPrefix, String bucket, int requestsPerMinute) {
    }
}
