package source.auth.application.service.validation.jwt.contracts;

import java.util.Collection;
import java.util.List;

public interface JwtServicer {
    String generateToken(long id);

    String generateToken(long id, Collection<String> roles);

    Long extractId(String token);

    default List<String> extractRoles(String token) {
        return List.of("USER");
    }
}
