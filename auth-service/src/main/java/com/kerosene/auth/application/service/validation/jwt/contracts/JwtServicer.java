package com.kerosene.auth.application.service.validation.jwt.contracts;

import java.util.Collection;
import java.util.List;

/** Authentication boundary for token issuance, claim extraction, and session revocation. */
public interface JwtServicer {
    /** Issues a USER token for an authenticated principal. @param id principal identifier @return signed token */
    String generateToken(long id);

    /** Issues a token with explicit authorities. @param id principal identifier @param roles authorities @return signed token */
    String generateToken(long id, Collection<String> roles);

    /** Issues a token for a specific session; implementations may preserve compatibility by ignoring the session. */
    /** @param id principal identifier @param roles authorities @param sessionId session revocation identifier @return signed token */
    default String generateToken(long id, Collection<String> roles, String sessionId) {
        return generateToken(id, roles);
    }

    /** Extracts the authenticated principal identifier. @param token signed token @return principal identifier */
    Long extractId(String token);

    /** Extracts an optional revocable session identifier. @param token signed token @return session ID or null */
    default String extractSessionId(String token) {
        return null;
    }

    /** Checks whether the token session was revoked. @param token signed token @return revocation state */
    default boolean isSessionRevoked(String token) {
        return false;
    }

    /** Revokes the token session until its expiration. @param token signed token */
    default void revokeSession(String token) {
    }

    /** Extracts token authorities, using USER for implementations without role support. @param token signed token @return roles */
    default List<String> extractRoles(String token) {
        return List.of("USER");
    }
}
