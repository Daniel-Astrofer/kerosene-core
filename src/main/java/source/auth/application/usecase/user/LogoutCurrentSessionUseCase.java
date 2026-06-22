package source.auth.application.usecase.user;

import org.springframework.stereotype.Component;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;

@Component
public class LogoutCurrentSessionUseCase {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtServicer jwtService;

    public LogoutCurrentSessionUseCase(JwtServicer jwtService) {
        this.jwtService = jwtService;
    }

    public Result execute(String authorization) {
        String token = extractBearerToken(authorization);
        if (token == null) {
            return new Result(Status.MISSING_TOKEN);
        }

        try {
            jwtService.revokeSession(token);
            return new Result(Status.REVOKED);
        } catch (RuntimeException exception) {
            return new Result(Status.REVOCATION_FAILED);
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    public enum Status {
        REVOKED,
        MISSING_TOKEN,
        REVOCATION_FAILED
    }

    public record Result(Status status) {
    }
}
