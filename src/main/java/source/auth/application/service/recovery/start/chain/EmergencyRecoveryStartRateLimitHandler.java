package source.auth.application.service.recovery.start.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.recovery.RecoveryRateLimitService;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;

@Component
@Order(20)
public class EmergencyRecoveryStartRateLimitHandler extends AbstractEmergencyRecoveryStartHandler {

    private final RecoveryRateLimitService rateLimitService;

    public EmergencyRecoveryStartRateLimitHandler(RecoveryRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public void handle(EmergencyRecoveryStartContext context) {
        rateLimitService.enforceStartAttempt(context.normalizedUsername(), context.clientFingerprint());
        handleNext(context);
    }
}
