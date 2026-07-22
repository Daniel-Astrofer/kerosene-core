package source.auth.application.orchestrator.login;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import source.auth.AuthExceptions;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

@Component
public class StartLogin {

    public static final long PRE_AUTH_TTL_SECONDS = 300L;

    private final LoginVerifier verifier;
    private final RedisServicer redisService;
    private final LoginThrottlePolicy throttlePolicy;
    private final IssueSessionToken issueSessionToken;

    public StartLogin(LoginVerifier verifier,
            RedisServicer redisService,
            LoginThrottlePolicy throttlePolicy,
            IssueSessionToken issueSessionToken) {
        this.verifier = verifier;
        this.redisService = redisService;
        this.throttlePolicy = throttlePolicy;
        this.issueSessionToken = issueSessionToken;
    }

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

    private String requireUsername(UserDTOContract dto) {
        if (dto == null || dto.getUsername() == null) {
            throw new AuthExceptions.InvalidCredentials("Username required.");
        }
        return dto.getUsername();
    }

    private void ensureNoAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !auth.getName().equalsIgnoreCase("anonymousUser")) {
            throw new AuthExceptions.InvalidCredentials("Usuário já está autenticado.");
        }
    }

    public static String preAuthKey(String preAuthToken) {
        return "pre_auth:" + preAuthToken;
    }
}
