package source.auth.application.service.validation.jwt;

import source.auth.AuthConstants;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service("JwtService")
public class JwtService implements JwtServicer {

    @Value("${api.secret.token.secret}")
    private String secretKey;

    public SecretKey getSecretKey() {
        byte[] keyBytes = secretKey.trim().getBytes(StandardCharsets.UTF_8);
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateToken(long id, String devicehash) {

        return Jwts.builder()
                .subject(devicehash)
                .id(String.valueOf(id))
                .claim("devicehash", devicehash)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + AuthConstants.JWT_EXPIRATION_TIME))
                .signWith(getSecretKey())
                .compact();

    }

    @Override
    public String extractDevice(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
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

}
