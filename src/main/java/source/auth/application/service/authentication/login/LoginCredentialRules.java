package source.auth.application.service.authentication.login;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.port.out.AuthUserGateway;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

@Service
public class LoginCredentialRules {

    private final AuthUserGateway userGateway;
    private final Hasher hasher;
    private final RedisServicer redisService;

    public LoginCredentialRules(AuthUserGateway userGateway,
            @Qualifier("Argon2Hasher") Hasher hasher,
            RedisServicer redisService) {
        this.userGateway = userGateway;
        this.hasher = hasher;
        this.redisService = redisService;
    }

    public void ensureRequestPresent(UserDTOContract dto) {
        if (dto == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

    public void ensureUsernamePresent(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

    public String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    public String registerRateLimitAttempt(String normalizedUsername) {
        String rateLimitKey = "rl:login:" + normalizedUsername;
        Long attempts = redisService.increment(rateLimitKey);
        if (attempts == 1L) {
            redisService.expire(rateLimitKey, 60);
        }
        if (attempts > 5L) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas. O motor anti-brute force foi ativado.");
        }
        return rateLimitKey;
    }

    public UserDataBase loadUser(String normalizedUsername) {
        UserDataBase user = userGateway.findByUsername(normalizedUsername);
        if (user == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
        return user;
    }

    public char[] normalizePassphrase(char[] input) {
        if (input == null) {
            return new char[0];
        }
        StringBuilder sb = new StringBuilder();
        boolean inSpace = false;
        for (char c : input) {
            if (Character.isWhitespace(c) || c == '\u00A0') {
                if (!inSpace) {
                    sb.append(' ');
                    inSpace = true;
                }
            } else {
                sb.append(c);
                inSpace = false;
            }
        }
        String normalized = sb.toString().trim();
        char[] clean = new char[normalized.length()];
        for (int i = 0; i < clean.length; i++) {
            clean[i] = normalized.charAt(i);
        }
        return clean;
    }

    public void verifyPassphrase(char[] normalizedPassphrase, UserDataBase user) {
        if (!hasher.verify(normalizedPassphrase, user.getPassphrase())) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

    public void clearRateLimit(String rateLimitKey) {
        if (rateLimitKey != null) {
            redisService.deleteValue(rateLimitKey);
        }
    }

    public void wipeSecrets(LoginValidationContext context) {
        if (context == null) {
            return;
        }
        if (context.getNormalizedPassphrase() != null) {
            Arrays.fill(context.getNormalizedPassphrase(), '\0');
        }
        UserDTOContract dto = context.getDto();
        if (dto != null && dto.getPassphrase() != null) {
            Arrays.fill(dto.getPassphrase(), '\0');
        }
    }
}
