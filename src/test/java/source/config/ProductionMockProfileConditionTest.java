package source.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import source.config.production.ProductionProfileDetector;
import source.config.production.ProductionSafetyCheckChain;

class ProductionMockProfileConditionTest {

    @Test
    void shouldFailFastWhenUnsafeProductionSettingsAreDetected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("bitcoin.mock-mode", "true");
        environment.setProperty("vault.enabled", "false");
        environment.setProperty("mpc.sidecar.tls.enabled", "false");
        environment.setProperty("app.cors.allowed-origins", "*");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("forbiddenMockService", new ForbiddenMockService());

        ProductionMockProfileCondition condition = new ProductionMockProfileCondition(
                new ProductionProfileDetector(environment),
                new ProductionSafetyCheckChain(environment, beanFactory));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> condition.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(exception.getMessage().contains(
                "bean " + ForbiddenMockService.class.getName() + " is not allowed in prod"));
        assertTrue(exception.getMessage().contains("bitcoin.mock-mode=true"));
        assertTrue(exception.getMessage().contains("vault.enabled must be true"));
        assertTrue(exception.getMessage().contains("mpc.sidecar.tls.enabled must be true"));
        assertTrue(exception.getMessage().contains("wildcard CORS is not allowed"));
        assertTrue(exception.getMessage().contains("quorum.shard.urls must define remote shard peers"));
        assertTrue(exception.getMessage().contains("custody.base-url must be configured"));
    }

    @Test
    void shouldSkipValidationOutsideProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("bitcoin.mock-mode", "true");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("forbiddenMockService", new ForbiddenMockService());

        ProductionMockProfileCondition condition = new ProductionMockProfileCondition(
                new ProductionProfileDetector(environment),
                new ProductionSafetyCheckChain(environment, beanFactory));

        assertDoesNotThrow(() -> condition.run(new DefaultApplicationArguments(new String[0])));
    }

    private static final class ForbiddenMockService {
    }
}
