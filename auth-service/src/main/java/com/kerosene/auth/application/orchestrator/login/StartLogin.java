package com.kerosene.auth.application.orchestrator.login;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.authentication.contracts.LoginVerifier;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.entity.UserDataBase;

/** Validates primary login credentials and starts either direct or two-factor authentication. */
@Component
public class StartLogin {

    /** Maximum lifetime of a pending pre-authentication token in Redis. */
    public static final long PRE_AUTH_TTL_SECONDS = 300L;

    /** Credential verifier for username and password authentication. */
    private final LoginVerifier verifier;
    /** Redis boundary for temporary pre-authentication token state. */
    private final RedisServicer redisService;
    /** Policy for primary credential throttling. */
    private final LoginThrottlePolicy throttlePolicy;
    /** Issues a full session immediately when no second factor is configured. */
    private final IssueSessionToken issueSessionToken;

    /**
     * Creates the primary login stage.
     *
     * @param verifier service that validates credentials and loads the user
     * @param redisService temporary state storage
     * @param throttlePolicy failure tracking and blocking rules
     * @param issueSessionToken session issuer for accounts without TOTP
     */
    public StartLogin(LoginVerifier verifier,
            RedisServicer redisService,
            LoginThrottlePolicy throttlePolicy,
            IssueSessionToken issueSessionToken) {
        this.verifier = verifier;
        this.redisService = redisService;
        this.throttlePolicy = throttlePolicy;
        this.issueSessionToken = issueSessionToken;
    }

    /**
     * Checks the account throttle and authentication context, then validates credentials.
     * Accounts without TOTP receive a session directly; others receive a random Redis-backed
     * pre-auth token. Invalid credentials increment the normalized username's failure counter.
     *
     * @param dto submitted credentials
     * @return session response for single-factor accounts or pre-auth token for two-factor accounts
     * @throws AuthExceptions.InvalidCredentials when input is invalid, already authenticated,
     *         throttled, or credentials fail
     */
    public String start(UserDTOContract dto) {
        String username = requireUsername(dto);
        String throttleUsername = username.toLowerCase(Locale.ROOT);
        throttlePolicy.ensureLoginAllowed(throttleUsername);
        ensureNoAuthenticatedUser();

        try {
            UserDataBase user = verifier.matcherWithoutDevice(dto);
            if (!user.hasTotpEnabled()) {
                throttlePolicy.clearLoginFailures(throttleUsername);
                return issueSessionToken.issue(user);
            }
            String preAuthToken = UUID.randomUUID().toString();
            redisService.setValue(preAuthKey(preAuthToken), user.getUsername(), PRE_AUTH_TTL_SECONDS);
            throttlePolicy.clearLoginFailures(throttleUsername);
            return preAuthToken;
        } catch (AuthExceptions.InvalidCredentials e) {
            throttlePolicy.recordLoginFailure(throttleUsername);
            throw e;
        }
    }

    /** Extracts the submitted username and rejects a missing DTO or username. */
    /** @param dto submitted login data */
    /** @return submitted username as provided */
    /** @throws AuthExceptions.InvalidCredentials when DTO or username is absent */
    private String requireUsername(UserDTOContract dto) {
        if (dto == null || dto.getUsername() == null) {
            throw new AuthExceptions.InvalidCredentials("Username required.");
        }
        return dto.getUsername();
    }

    /** Prevents beginning a login while a non-anonymous principal is already authenticated. */
    /** @throws AuthExceptions.InvalidCredentials when a user is already authenticated */
    private void ensureNoAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !auth.getName().equalsIgnoreCase("anonymousUser")) {
            throw new AuthExceptions.InvalidCredentials("Usuário já está autenticado.");
        }
    }

    /** Builds the Redis key for a pending pre-authentication token. */
    /** @param preAuthToken opaque random token returned to the client */
    /** @return namespaced Redis key */
    public static String preAuthKey(String preAuthToken) {
        return "pre_auth:" + preAuthToken;
    }
}
