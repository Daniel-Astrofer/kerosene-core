package source.config.production;

import java.util.Map;
import source.auth.application.service.identityaccess.PlatformTransactionSignerPort;

public class PlatformMpcSigningProductionSafetyCheck extends AbstractProductionSafetyCheck {

    private static final String UNAVAILABLE_MESSAGE = "platform MPC signer must be available in prod";

    public PlatformMpcSigningProductionSafetyCheck(ProductionSafetyCheck next) {
        super(next);
    }

    @Override
    protected void inspect(ProductionSafetyContext context) {
        Map<String, PlatformTransactionSignerPort> signers;
        try {
            signers = context.beanFactory().getBeansOfType(PlatformTransactionSignerPort.class, false, false);
        } catch (RuntimeException exception) {
            context.addViolation("platform MPC signer availability could not be verified");
            return;
        }

        if (signers.isEmpty()) {
            context.addViolation(UNAVAILABLE_MESSAGE);
            return;
        }

        boolean hasAvailableSigner = false;
        for (Map.Entry<String, PlatformTransactionSignerPort> signer : signers.entrySet()) {
            try {
                if (signer.getValue().isAvailable()) {
                    hasAvailableSigner = true;
                    break;
                }
            } catch (RuntimeException exception) {
                context.addViolation("platform MPC signer " + signer.getKey() + " availability check failed");
            }
        }

        if (!hasAvailableSigner) {
            context.addViolation(UNAVAILABLE_MESSAGE);
        }
    }
}
