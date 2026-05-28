package source.auth.application.service.recovery.start.chain;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.AuthExceptions;
import source.auth.application.service.recovery.RecoveryCodeService;
import source.auth.application.service.recovery.RecoveryRateLimitService;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;

@Component
@Order(50)
public class EmergencyRecoveryStartCodeMatchHandler extends AbstractEmergencyRecoveryStartHandler {

    private final RecoveryCodeService recoveryCodeService;
    private final RecoveryRateLimitService rateLimitService;

    public EmergencyRecoveryStartCodeMatchHandler(RecoveryCodeService recoveryCodeService,
            RecoveryRateLimitService rateLimitService) {
        this.recoveryCodeService = recoveryCodeService;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public void handle(EmergencyRecoveryStartContext context) {
        List<String> matchedHashes = recoveryCodeService.matchRecoveryCodes(
                context.normalizedRecoveryCodes(),
                context.user().getBackupCodes());
        if (matchedHashes.size() != context.normalizedRecoveryCodes().size()) {
            rateLimitService.registerFailure(context.normalizedUsername(), context.clientFingerprint());
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. Verify the recovery codes and retry.");
        }

        rateLimitService.clearFailures(context.normalizedUsername(), context.clientFingerprint());
        context.setMatchedRecoveryCodeHashes(matchedHashes);
        handleNext(context);
    }
}
