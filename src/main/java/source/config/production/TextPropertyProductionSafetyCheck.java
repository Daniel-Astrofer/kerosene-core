package source.config.production;

import java.util.List;

public class TextPropertyProductionSafetyCheck extends AbstractProductionSafetyCheck {

    private static final List<String> REQUIRED_NON_BLANK_PROPERTIES = List.of(
            "custody.base-url",
            "custody.api-key",
            "lightning.provider.base-url",
            "lightning.provider.api-key");

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

        for (String propertyName : REQUIRED_NON_BLANK_PROPERTIES) {
            if (context.environment().getProperty(propertyName, "").isBlank()) {
                context.addViolation(propertyName + " must be configured");
            }
        }
    }
}
