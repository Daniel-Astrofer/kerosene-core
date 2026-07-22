package source.config.production;

public class TextPropertyProductionSafetyCheck extends AbstractProductionSafetyCheck {

    public TextPropertyProductionSafetyCheck(ProductionSafetyCheck next) {
        super(next);
    }

    @Override
    protected void inspect(ProductionSafetyContext context) {
        String corsOrigins = context.environment().getProperty("app.cors.allowed-origins", "");
        if (corsOrigins.isBlank()) {
            context.addViolation("app.cors.allowed-origins must be configured");
        }
        if (corsOrigins.contains("*")) {
            context.addViolation("wildcard CORS is not allowed");
        }
        if (corsOrigins.contains("localhost") || corsOrigins.contains("127.0.0.1")) {
            context.addViolation("localhost CORS origins are not allowed in prod");
        }

        String relyingPartyId = context.environment().getProperty("webauthn.relying-party-id", "");
        if (relyingPartyId.isBlank() || "localhost".equalsIgnoreCase(relyingPartyId)) {
            context.addViolation("webauthn.relying-party-id must be a production host");
        }

        String quorumPeers = context.environment().getProperty("quorum.shard.urls", "");
        if (quorumPeers.isBlank()) {
            context.addViolation("quorum.shard.urls must define remote shard peers");
        }

        for (String propertyName : java.util.List.of(
                "lightning.lnd.host",
                "lightning.lnd.tls.cert-path",
                "bitcoin.platform.master-xpub",
                "quorum.psbt.signer-urls",
                "quorum.psbt.signer-ids",
                "shard.attestation.secret",
                "mpc.sidecar.host",
                "mpc.sidecar.tls.cert-chain",
                "mpc.sidecar.tls.private-key",
                "mpc.sidecar.tls.trust-cert-collection")) {
            if (context.environment().getProperty(propertyName, "").isBlank()) {
                context.addViolation(propertyName + " must be configured");
            }
        }

        String macaroon = context.environment().getProperty("lightning.lnd.macaroon", "");
        String macaroonPath = context.environment().getProperty("lightning.lnd.macaroon-path", "");
        if (macaroon.isBlank() && macaroonPath.isBlank()) {
            context.addViolation("lightning.lnd.macaroon or lightning.lnd.macaroon-path must be configured");
        }
    }
}
