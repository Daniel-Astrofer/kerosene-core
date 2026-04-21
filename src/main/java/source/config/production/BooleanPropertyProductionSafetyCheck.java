package source.config.production;

import java.util.List;

public class BooleanPropertyProductionSafetyCheck extends AbstractProductionSafetyCheck {

    private static final List<String> PROHIBITED_TRUE_FLAGS = List.of(
            "bitcoin.mock-mode",
            "custody.mock-mode",
            "app.dev.inject-test-balance",
            "quorum.allow-local-simulation");

    public BooleanPropertyProductionSafetyCheck(ProductionSafetyCheck next) {
        super(next);
    }

    @Override
    protected void inspect(ProductionSafetyContext context) {
        for (String flag : PROHIBITED_TRUE_FLAGS) {
            if (context.environment().getProperty(flag, Boolean.class, false)) {
                context.addViolation(flag + "=true");
            }
        }

        requireTrue(context, "vault.enabled", false);
        requireTrue(context, "mpc.sidecar.tls.enabled", true);
    }

    private void requireTrue(ProductionSafetyContext context, String propertyName, boolean defaultValue) {
        if (!context.environment().getProperty(propertyName, Boolean.class, defaultValue)) {
            context.addViolation(propertyName + " must be true");
        }
    }
}
