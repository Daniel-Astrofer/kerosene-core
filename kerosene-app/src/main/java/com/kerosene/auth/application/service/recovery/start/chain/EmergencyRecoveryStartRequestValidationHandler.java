package source.auth.application.service.recovery.start.chain;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.recovery.RecoveryCodeService;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;
import source.auth.dto.EmergencyRecoveryStartRequest;

@Component
@Order(10)
public class EmergencyRecoveryStartRequestValidationHandler extends AbstractEmergencyRecoveryStartHandler {

    private final SignupVerifier signupVerifier;
    private final RecoveryCodeService recoveryCodeService;

    @Value("${auth.recovery.required-backup-codes:3}")
    private int requiredRecoveryCodes;

    public EmergencyRecoveryStartRequestValidationHandler(SignupVerifier signupVerifier,
            RecoveryCodeService recoveryCodeService) {
        this.signupVerifier = signupVerifier;
        this.recoveryCodeService = recoveryCodeService;
    }

    @Override
    public void handle(EmergencyRecoveryStartContext context) {
        EmergencyRecoveryStartRequest request = context.request();
        if (request == null) {
            throw new IllegalArgumentException("Recovery request body is required.");
        }

        String normalizedUsername = normalizeUsername(request.getUsername());
        signupVerifier.checkUsernameNotNull(normalizedUsername);
        signupVerifier.checkUsernameFormat(normalizedUsername);
        signupVerifier.checkUsernameLength(normalizedUsername);
        signupVerifier.checkPassphraseNotNull(request.getNewPassphrase());
        signupVerifier.checkPassphraseLength(request.getNewPassphrase());
        char[] passphraseCopy = copyCharArray(request.getNewPassphrase());
        try {
            signupVerifier.checkPassphraseBip39(passphraseCopy);
        } finally {
            if (passphraseCopy != null) {
                java.util.Arrays.fill(passphraseCopy, '\0');
            }
        }

        List<String> normalizedCodes = recoveryCodeService.normalizeRecoveryCodes(request.getRecoveryCodes());
        if (normalizedCodes.size() < requiredRecoveryCodes) {
            throw new IllegalArgumentException(
                    "At least " + requiredRecoveryCodes + " distinct recovery codes are required.");
        }

        if (request.getChallenge() == null || request.getChallenge().isBlank()
                || request.getNonce() == null || request.getNonce().isBlank()) {
            throw new IllegalArgumentException("Proof of Work challenge and nonce are required.");
        }

        context.setNormalizedUsername(normalizedUsername);
        context.setNormalizedRecoveryCodes(normalizedCodes);
        handleNext(context);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private char[] copyCharArray(char[] input) {
        if (input == null) {
            return null;
        }
        char[] copy = new char[input.length];
        System.arraycopy(input, 0, copy, 0, input.length);
        return copy;
    }
}
