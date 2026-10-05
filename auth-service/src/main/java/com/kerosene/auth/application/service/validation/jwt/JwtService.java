package com.kerosene.auth.application.service.validation.jwt;

import com.kerosene.auth.AuthConstants;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.application.service.validation.jwt.contracts.JwtServicer;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Issues and validates signed JWTs, including role claims and optional Redis-backed session revocation. */
@Service("JwtService")
public class JwtService implements JwtServicer {

    /** Configured HMAC signing material; at least 32 UTF-8 bytes are required. */
    @Value("${api.secret.token.secret}")
    private String secretKey;

    /** Issuer claim identifying the authentication service. */
    @Value("${kfe.auth.jwt.issuer:Kerosene-Auth}")
    private String jwtIssuer;

    /** Audience claim expected by Kerosene clients. */
    @Value("${kfe.auth.jwt.audience:kerosene-app}")
    private String jwtAudience;

    /** Optional store used to persist revoked session identifiers until token expiry. */
    @Autowired(required = false)
    private RedisServicer redisService;

    /** Rejects invalid signing configuration during startup, before authentication requests are served. */
    @PostConstruct
    void validateConfiguration() {
        // Fail before accepting signup; never discover an unusable signing key after commit.
        getSecretKey();
    }

    /** Validates the configured key length and constructs the HMAC key used for signing and verification. */
    /** @return HMAC signing key derived from the configured UTF-8 bytes */
    /** @throws IllegalStateException when the key is missing or shorter than 32 bytes */
    public SecretKey getSecretKey() {
        if (secretKey == null || secretKey.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT signing key must contain at least 32 UTF-8 bytes");
        }
        byte[] keyBytes = secretKey.trim().getBytes(StandardCharsets.UTF_8);
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
    }

    /** Issues a USER token and generates an identifier for its independently revocable session. */
    /** @param id authenticated user identifier */
    /** @return compact signed JWT */
    @Override
    public String generateToken(long id) {
        return generateToken(id, List.of("USER"));
    }

    /** Issues a token for the supplied roles with a newly generated session identifier. */
    /** @param id authenticated user identifier */
    /** @param roles authorities to include in the claim */
    /** @return compact signed JWT */
    @Override
    public String generateToken(long id, Collection<String> roles) {
        return generateToken(id, roles, UUID.randomUUID().toString());
    }

    /** Creates a signed token with stable subject, normalized roles, issuer, audience, and expiry claims. */
    /** @param id authenticated user identifier */
    /** @param roles authorities to normalize and include */
    /** @param sessionId revocation key, generated if blank */
    /** @return compact signed JWT */
    @Override
    public String generateToken(long id, Collection<String> roles, String sessionId) {
        return Jwts.builder()
                .subject(String.valueOf(id))
                .id(String.valueOf(id))
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .claim("sessionId", normalizeSessionId(sessionId))
                .claim("roles", normalizeRoles(roles))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + AuthConstants.JWT_EXPIRATION_TIME))
                .signWith(getSecretKey())
                .compact();
    }

    /** Verifies the signature and reads the numeric user identifier from the token ID claim. */
    /** @param token compact signed JWT */
    /** @return user identifier */
    @Override
    public Long extractId(String token) {
        String idString = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getId();
        return Long.parseLong(idString);
    }

    /** Verifies the token and returns its session identifier claim, if present. */
    /** @param token compact signed JWT */
    /** @return session identifier or null for a token without the claim */
    @Override
    public String extractSessionId(String token) {
        Object sessionId = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("sessionId");
        return sessionId == null ? null : String.valueOf(sessionId);
    }

    /** Checks the session revocation store when a session claim and Redis service are available. */
    /** @param token compact signed JWT */
    /** @return true when the token's session is recorded as revoked */
    @Override
    public boolean isSessionRevoked(String token) {
        String sessionId = extractSessionId(token);
        if (sessionId == null || sessionId.isBlank() || redisService == null) {
            return false;
        }
        return redisService.isJwtSessionRevoked(sessionId);
    }

    /** Revokes the token session for no longer than its remaining lifetime. */
    /** @param token compact signed JWT */
    @Override
    public void revokeSession(String token) {
        String sessionId = extractSessionId(token);
        if (sessionId == null || sessionId.isBlank() || redisService == null) {
            return;
        }
        long timeoutSeconds = Math.max(1L, (extractExpiration(token).getTime() - System.currentTimeMillis()) / 1000L);
        redisService.revokeJwtSession(sessionId, timeoutSeconds);
    }

    /** Returns normalized, distinct role claims or the USER fallback for legacy tokens. */
    /** @param token compact signed JWT */
    /** @return normalized roles carried by the signed token */
    @Override
    public List<String> extractRoles(String token) {
        Object rawRoles = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("roles");
        if (rawRoles instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .map(this::normalizeRole)
                    .filter(role -> !role.isBlank())
                    .distinct()
                    .toList();
        }
        return List.of("USER");
    }

    /** Extracts the verified expiration timestamp from a signed token. @param token compact JWT @return expiration instant */
    public Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    /** Returns whether the verified token has less time remaining than the configured renewal threshold. @param token compact JWT @return true when renewal is due */
    public boolean shouldRenewToken(String token) {
        Date expiration = extractExpiration(token);
        long timeRemaining = expiration.getTime() - System.currentTimeMillis();
        return timeRemaining < AuthConstants.JWT_RENEWAL_THRESHOLD;
    }

    /** Normalizes roles, removes blank and duplicate entries, and applies USER as the safe fallback. */
    /** @param roles candidate authorities */
    /** @return normalized distinct role names */
    private List<String> normalizeRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("USER");
        }
        List<String> normalized = roles.stream()
                .map(this::normalizeRole)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("USER") : normalized;
    }

    /** Trims and uppercases a role and removes the conventional ROLE_ prefix. */
    /** @param role candidate role, possibly null */
    /** @return normalized role or an empty string for null */
    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        String normalized = role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring("ROLE_".length());
        }
        return normalized;
    }

    /** Replaces a missing session identifier with a fresh UUID. */
    /** @param sessionId requested identifier */
    /** @return nonblank session identifier */
    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

}
