package source.config.production;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionProfileDetector {

    private final Environment environment;

    public ProductionProfileDetector(Environment environment) {
        this.environment = environment;
    }

    public boolean isProductionProfile() {
        boolean springProductionProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(this::isProductionName);

        String legacyActiveProfile = environment.getProperty("activeProfile", "");
        return springProductionProfile || isProductionName(legacyActiveProfile);
    }

    private boolean isProductionName(String profileName) {
        return "prod".equalsIgnoreCase(profileName) || "production".equalsIgnoreCase(profileName);
    }
}
