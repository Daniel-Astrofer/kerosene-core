package source.auth.application.service.recovery.start.chain;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.AuthExceptions;
import source.auth.application.port.out.AuthUserGateway;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.recovery.RecoveryCodeService;
import source.auth.application.service.recovery.RecoveryRateLimitService;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;
import source.auth.model.entity.UserDataBase;

@Component
@Order(40)
public class EmergencyRecoveryStartUserEligibilityHandler extends AbstractEmergencyRecoveryStartHandler {

    private final AuthUserGateway userGateway;
    private final Hasher hasher;
    private final RecoveryCodeService recoveryCodeService;
    private final RecoveryRateLimitService rateLimitService;

    public EmergencyRecoveryStartUserEligibilityHandler(AuthUserGateway userGateway,
            @Qualifier("Argon2Hasher") Hasher hasher,
            RecoveryCodeService recoveryCodeService,
            RecoveryRateLimitService rateLimitService) {
        this.userGateway = userGateway;
        this.hasher = hasher;
        this.recoveryCodeService = recoveryCodeService;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public void handle(EmergencyRecoveryStartContext context) {
        UserDataBase user = userGateway.findByUsername(context.normalizedUsername());
        if (user == null || user.getBackupCodes() == null
                || user.getBackupCodes().size() < context.normalizedRecoveryCodes().size()) {
            recoveryCodeService.burnRecoveryCodeChecks(context.normalizedRecoveryCodes());
            rateLimitService.registerFailure(context.normalizedUsername(), context.clientFingerprint());
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. Verify the recovery codes and retry.");
        }

        char[] newPassphraseCopy = copyCharArray(context.request().getNewPassphrase());
        try {
            if (Boolean.TRUE.equals(hasher.verify(newPassphraseCopy, user.getPassphrase()))) {
                throw new AuthExceptions.InvalidPassphrase(
                        "The new passphrase must be different from the current passphrase.");
            }
        } finally {
            if (newPassphraseCopy != null) {
                java.util.Arrays.fill(newPassphraseCopy, '\0');
            }
        }

        context.setUser(user);
        handleNext(context);
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
