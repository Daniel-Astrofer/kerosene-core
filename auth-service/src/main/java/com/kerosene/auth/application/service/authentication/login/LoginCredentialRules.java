package com.kerosene.auth.application.service.authentication.login;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.kerosene.auth.AuthConstants;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.port.out.AuthUserGateway;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.application.service.crypto.contracts.Hasher;
import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.entity.UserDataBase;

/** Implements reusable username, rate-limit, account lookup, and passphrase rules for login. */
@Service
public class LoginCredentialRules {

    /** Outbound account lookup port. */
    private final AuthUserGateway userGateway;
    /** Argon2-qualified passphrase verifier. */
    private final Hasher hasher;
    /** Redis counters for brute-force throttling. */
    private final RedisServicer redisService;

    /** Creates the login rules with persistence, hashing, and rate-limit boundaries. */
    /** @param userGateway account lookup port */
    /** @param hasher Argon2 passphrase verifier */
    /** @param redisService Redis rate-limit service */
    public LoginCredentialRules(AuthUserGateway userGateway,
            @Qualifier("Argon2Hasher") Hasher hasher,
            RedisServicer redisService) {
        this.userGateway = userGateway;
        this.hasher = hasher;
        this.redisService = redisService;
    }

    /** Rejects a missing login DTO using the generic invalid-credentials response. */
    /** @param dto submitted login request */
    /** @throws AuthExceptions.InvalidCredentials when request is null */
    public void ensureRequestPresent(UserDTOContract dto) {
        if (dto == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

    /** Rejects a null or blank normalized username. */
    /** @param username normalized candidate */
    /** @throws AuthExceptions.InvalidCredentials when username is absent */
    public void ensureUsernamePresent(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

    /** Trims and lowercases a username before lookups and throttle key construction. */
    /** @param username raw username, possibly null */
    /** @return normalized username or null when input is null */
    public String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    /** Increments the 60-second login counter, allows degraded Redis to fail open, and rejects after five attempts. */
    /** @param normalizedUsername canonical username used in the counter key */
    /** @return rate-limit key to clear after successful credential verification */
    /** @throws AuthExceptions.InvalidCredentials when attempt count exceeds five */
    public String registerRateLimitAttempt(String normalizedUsername) {
        String rateLimitKey = "rl:login:" + normalizedUsername;
        Long attempts = redisService.increment(rateLimitKey);
        // null = Redis degraded; allow login path to continue (fail-open).
        if (attempts == null) {
            return rateLimitKey;
        }
        if (attempts == 1L) {
            redisService.expire(rateLimitKey, 60);
        }
        if (attempts > 5L) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas. O motor anti-brute force foi ativado.");
        }
        return rateLimitKey;
    }

    /** Loads the account or returns generic invalid credentials to avoid disclosing account existence. */
    /** @param normalizedUsername canonical username */
    /** @return persisted account */
    /** @throws AuthExceptions.InvalidCredentials when no account matches */
    public UserDataBase loadUser(String normalizedUsername) {
        UserDataBase user = userGateway.findByUsername(normalizedUsername);
        if (user == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
        return user;
    }

    /** Collapses whitespace runs to one space and trims the mutable passphrase into a new character array. */
    /** @param input raw passphrase characters, possibly null */
    /** @return normalized mutable character array */
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

    /** Compares normalized passphrase characters to the account's stored hash. */
    /** @param normalizedPassphrase candidate characters */
    /** @param user account containing the stored passphrase hash */
    /** @throws AuthExceptions.InvalidCredentials when the hash does not match */
    public void verifyPassphrase(char[] normalizedPassphrase, UserDataBase user) {
        if (!hasher.verify(normalizedPassphrase, user.getPassphrase())) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

    /** Removes a successful-login rate-limit key when one was registered. */
    /** @param rateLimitKey key returned by {@link #registerRateLimitAttempt(String)} */
    public void clearRateLimit(String rateLimitKey) {
        if (rateLimitKey != null) {
            redisService.deleteValue(rateLimitKey);
        }
    }

    /** Zeroes normalized and request-owned passphrase arrays to reduce secret lifetime in memory. */
    /** @param context validation context holding the secret buffers */
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
