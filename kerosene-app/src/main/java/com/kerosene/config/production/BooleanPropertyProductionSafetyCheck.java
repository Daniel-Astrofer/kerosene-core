package source.config.production;

import java.util.List;

public class BooleanPropertyProductionSafetyCheck extends AbstractProductionSafetyCheck {

    private static final List<String> PROHIBITED_TRUE_FLAGS = List.of(
            "bitcoin.mock-mode",
            "custody.mock-mode",
            "quorum.allow-local-simulation",
            "treasury.siphon.manual-settlement-enabled",
            "transactions.onchain.test-instant-settlement-enabled");

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
        requireTrue(context, "vault.raft.enabled", false);
        requireTrue(context, "vault.raft.required", false);
        requireTrue(context, "mpc.sidecar.tls.enabled", true);
        requireTrue(context, "lightning.lnd.enabled", false);
        requireTrue(context, "bitcoin.rpc.enabled", false);
        requireTrue(context, "bitcoin.rpc.required", false);
        requireTrue(context, "bitcoin.rpc.pruned-required", false);
        requireTrue(context, "tor.health.required", false);
        requireTrue(context, "release.attestation.required", false);
        requireTrue(context, "release.attestation.remote.enabled", false);
        requireTrue(context, "quorum.psbt.require-signer-identity", true);
    }

    private void requireTrue(ProductionSafetyContext context, String propertyName, boolean defaultValue) {
        if (!context.environment().getProperty(propertyName, Boolean.class, defaultValue)) {
            context.addViolation(propertyName + " must be true");
        }
    }
}
