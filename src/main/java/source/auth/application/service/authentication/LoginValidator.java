package source.auth.application.service.authentication;

import source.auth.application.service.authentication.login.LoginCredentialRules;
import source.auth.application.service.authentication.login.LoginValidationContext;
import source.auth.application.service.authentication.login.chain.LoginValidationChain;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import org.springframework.stereotype.Service;

/**
 * Service responsible for authenticating users during login.
 * Validates credentials and device information.
 */
@Service
public class LoginValidator implements LoginVerifier {

    private final LoginCredentialRules rules;
    private final LoginValidationChain validationChain;

    public LoginValidator(LoginCredentialRules rules,
            LoginValidationChain validationChain) {
        this.rules = rules;
        this.validationChain = validationChain;
    }

    /**
     * Matches user credentials without validating device information.
     *
     * @param dto the user credentials
     * @return the authenticated user entity
     */
    @Override
    public UserDataBase matcherWithoutDevice(UserDTOContract dto) {
        LoginValidationContext context = new LoginValidationContext(dto);
        try {
            validationChain.validate(context);
            rules.clearRateLimit(context.getRateLimitKey());
            return context.getUser();
        } finally {
            rules.wipeSecrets(context);
        }
    }

    /**
     * Finds a user by username only — used by the TOTP verification step
     * where the passphrase was already validated in the initial login request.
     */
    @Override
    public UserDataBase findByUsernameOnly(String username) {
        String normalizedUsername = rules.normalizeUsername(username);
        rules.ensureUsernamePresent(normalizedUsername);
        return rules.loadUser(normalizedUsername);
    }

}
