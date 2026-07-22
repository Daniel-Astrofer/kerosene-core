package source.auth.application.service.validation.jwt.contracts;

import java.util.Collection;
import java.util.List;

public interface JwtServicer {
    String generateToken(long id);

    String generateToken(long id, Collection<String> roles);

    default String generateToken(long id, Collection<String> roles, String sessionId) {
        return generateToken(id, roles);
    }

    Long extractId(String token);

    default String extractSessionId(String token) {
        return null;
    }

    default boolean isSessionRevoked(String token) {
        return false;
    }

    default void revokeSession(String token) {
    }

    default List<String> extractRoles(String token) {
        return List.of("USER");
    }
}
