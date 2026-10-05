package com.kerosene.auth.application.service.authentication;

import com.kerosene.auth.application.service.authentication.login.LoginCredentialRules;
import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;
import com.kerosene.auth.application.service.authentication.login.chain.LoginValidationChain;
import com.kerosene.auth.application.service.authentication.contracts.LoginVerifier;
import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.entity.UserDataBase;
import org.springframework.stereotype.Service;

/**
 * Service responsible for authenticating users during login.
 * Validates credentials and device information.
 */
/** Adapts login requests to an ordered credential-validation chain and wipes secret buffers afterward. */
@Service
public class LoginValidator implements LoginVerifier {

    /** Rule operations shared by ordered login handlers. */
    private final LoginCredentialRules rules;
    /** Ordered sequence for required-field, throttling, lookup, and passphrase checks. */
    private final LoginValidationChain validationChain;

    /** Creates the login validator. */
    /** @param rules reusable login validation and cleanup operations */
    /** @param validationChain ordered login rule chain */
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
     * @throws com.kerosene.auth.AuthExceptions.InvalidCredentials when validation fails
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
     * Finds a user by normalized username only, for the second-factor step after the initial
     * credential request already validated the passphrase.
     * @param username submitted username
     * @return persisted user
     */
    @Override
    public UserDataBase findByUsernameOnly(String username) {
        String normalizedUsername = rules.normalizeUsername(username);
        rules.ensureUsernamePresent(normalizedUsername);
        return rules.loadUser(normalizedUsername);
    }

}
