package source.auth.application.service.recovery.start.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.AuthExceptions;
import source.auth.application.service.pow.PowService;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;

@Component
@Order(30)
public class EmergencyRecoveryStartProofOfWorkHandler extends AbstractEmergencyRecoveryStartHandler {

    private final PowService powService;

    public EmergencyRecoveryStartProofOfWorkHandler(PowService powService) {
        this.powService = powService;
    }

    @Override
    public void handle(EmergencyRecoveryStartContext context) {
        if (!powService.verifyChallenge(context.request().getChallenge(), context.request().getNonce())) {
            throw new AuthExceptions.InvalidCredentials(
                    "Invalid or expired Proof of Work. Please request a new challenge and calculate the correct nonce.");
        }
        handleNext(context);
    }
}
