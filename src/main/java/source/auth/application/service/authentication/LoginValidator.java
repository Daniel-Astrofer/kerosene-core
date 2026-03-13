package source.auth.application.service.authentication;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service responsible for authenticating users during login.
 * Validates credentials and device information.
 */
@Service
public class LoginValidator implements LoginVerifier {

    private final UserRepository repository;
    private final Hasher hasher;
    private final source.auth.application.service.cache.contracts.RedisServicer redisService;

    public LoginValidator(UserRepository repository,
            @Qualifier("Argon2Hasher") Hasher hasher,
            source.auth.application.service.cache.contracts.RedisServicer redisService) {
        this.repository = repository;
        this.hasher = hasher;
        this.redisService = redisService;
    }

    /**
     * Matches user credentials without validating device information.
     *
     * @param dto the user credentials
     * @return the authenticated user entity
     */
    @Override
    public UserDataBase matcherWithoutDevice(UserDTOContract dto) {
        String username = dto.getUsername();

        // 3. DoS Protection - Pre-Argon2id Rate Limiter
        if (username == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
        String rlKey = "rl:login:" + username.toLowerCase();
        Long attempts = redisService.increment(rlKey);
        if (attempts == 1L) {
            redisService.expire(rlKey, 60); // 1 minuto de janela
        }
        if (attempts > 5L) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas. O motor anti-brute force foi ativado.");
        }

        UserDataBase user = repository.findByUsername(username);

        if (user == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }

        char[] normalizedPassphrase = normalizePassphrase(dto.getPassphrase());

        // Use verify() correctly because Argon2 always generates a random salt per hash
        boolean isValid = hasher.verify(normalizedPassphrase, user.getPassphrase());
        java.util.Arrays.fill(normalizedPassphrase, '\0');
        java.util.Arrays.fill(dto.getPassphrase(), '\0');

        if (!isValid) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }

        // Sucesso: resetamos o limitador
        redisService.deleteValue(rlKey);

        return user;
    }

    /**
     * Finds a user by username only — used by the TOTP verification step
     * where the passphrase was already validated in the initial login request.
     */
    @Override
    public UserDataBase findByUsernameOnly(String username) {
        UserDataBase user = repository.findByUsername(username);
        if (user == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
        return user;
    }

    private char[] normalizePassphrase(char[] input) {
        if (input == null)
            return new char[0];
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
        String stringRef = sb.toString().trim();
        // For a true zero-allocation we should iterate in a newly sized char[].
        char[] clean = new char[stringRef.length()];
        for (int i = 0; i < clean.length; i++)
            clean[i] = stringRef.charAt(i);
        return clean;
    }

}