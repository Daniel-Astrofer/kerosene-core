package source.auth.application.service.validation.jwt;

import source.auth.AuthConstants;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service("JwtService")
public class JwtService implements JwtServicer {

    @Value("${api.secret.token.secret}")
    private String secretKey;

    public SecretKey getSecretKey() {
        byte[] keyBytes = secretKey.trim().getBytes(StandardCharsets.UTF_8);
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateToken(long id) {
        return generateToken(id, List.of("USER"));
    }

    @Override
    public String generateToken(long id, Collection<String> roles) {
        return Jwts.builder()
                .subject(String.valueOf(id))
                .id(String.valueOf(id))
                .claim("roles", normalizeRoles(roles))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + AuthConstants.JWT_EXPIRATION_TIME))
                .signWith(getSecretKey())
                .compact();
    }

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

    /**
     * Extrai o tempo de expiração do token
     */
    public Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    /**
     * Verifica se o token está próximo da expiração e precisa ser renovado
     * Renova se menos de 1 hora restante
     */
    public boolean shouldRenewToken(String token) {
        Date expiration = extractExpiration(token);
        long timeRemaining = expiration.getTime() - System.currentTimeMillis();
        return timeRemaining < AuthConstants.JWT_RENEWAL_THRESHOLD;
    }

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

}
