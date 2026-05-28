package source.auth.application.service.authentication;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import source.auth.AuthExceptions;
import source.auth.application.port.out.AuthUserGateway;
import source.auth.application.service.authentication.signup.SignupCredentialRules;
import source.auth.application.service.authentication.signup.chain.SignupPassphrasePolicyHandler;
import source.auth.application.service.authentication.signup.chain.SignupRequiredFieldsHandler;
import source.auth.application.service.authentication.signup.chain.SignupUsernameAvailabilityHandler;
import source.auth.application.service.authentication.signup.chain.SignupUsernamePolicyHandler;
import source.auth.application.service.authentication.signup.chain.SignupValidationChain;

class SignupValidatorTest {

    private AuthUserGateway userGateway;
    private SignupValidator validator;

    @BeforeEach
    void setUp() {
        userGateway = mock(AuthUserGateway.class);

        SignupCredentialRules rules = new SignupCredentialRules(userGateway);
        SignupValidationChain chain = new SignupValidationChain(List.of(
                new SignupUsernameAvailabilityHandler(rules),
                new SignupPassphrasePolicyHandler(rules),
                new SignupRequiredFieldsHandler(rules),
                new SignupUsernamePolicyHandler(rules)));

        validator = new SignupValidator(rules, chain);
    }

    @Test
    void verifyShouldAcceptStrongPasswordAndAvailableUsername() {
        when(userGateway.existsByUsername("alice")).thenReturn(false);

        boolean result = validator.verify(
                "alice",
                "Sup3rSecure!12".toCharArray());

        assertTrue(result);
    }

    @Test
    void verifyShouldRejectExistingUsername() {
        when(userGateway.existsByUsername("alice")).thenReturn(true);

        assertThrows(AuthExceptions.UserAlreadyExistsException.class, () -> validator.verify(
                "alice",
                "Sup3rSecure!12".toCharArray()));
    }

    @Test
    void verifyShouldRejectWeakPassword() {
        when(userGateway.existsByUsername("alice")).thenReturn(false);

        assertThrows(AuthExceptions.InvalidPassphrase.class, () -> validator.verify(
                "alice",
                "weakpassword".toCharArray()));
    }
}
