package source.config.production;

public class TextPropertyProductionSafetyCheck extends AbstractProductionSafetyCheck {

    public TextPropertyProductionSafetyCheck(ProductionSafetyCheck next) {
        super(next);
    }

    @Override
    protected void inspect(ProductionSafetyContext context) {
        String corsOrigins = context.environment().getProperty("app.cors.allowed-origins", "");
        if (corsOrigins.contains("*")) {
            context.addViolation("wildcard CORS is not allowed");
        }

        String quorumPeers = context.environment().getProperty("quorum.shard.urls", "");
        if (quorumPeers.isBlank()) {
            context.addViolation("quorum.shard.urls must define remote shard peers");
        }

        for (String propertyName : java.util.List.of(
                "lightning.lnd.host",
                "lightning.lnd.tls.cert-path",
                "bitcoin.platform.master-xpub")) {
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
