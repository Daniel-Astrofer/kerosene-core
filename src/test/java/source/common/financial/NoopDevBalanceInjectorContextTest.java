package source.common.financial;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class NoopDevBalanceInjectorContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FinancialFallbackConfiguration.class);

    @Test
    void providesDefaultDevBalanceInjectorWhenNoOtherBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DevBalanceInjector.class);
            assertThat(context.getBean(DevBalanceInjector.class))
                    .isInstanceOf(NoopDevBalanceInjector.class);
            assertThat(context.getBean(DevBalanceInjector.class).isEnabled()).isFalse();
        });
    }

    @Test
    void providesDefaultFinancialNotificationAuditPortWhenNoOtherBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FinancialNotificationAuditPort.class);
            assertThat(context.getBean(FinancialNotificationAuditPort.class))
                    .isInstanceOf(NoopFinancialNotificationAuditPort.class);
        });
    }

    @Test
    void providesDefaultFinancialNotificationPortWhenNoOtherBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FinancialNotificationPort.class);
            assertThat(context.getBean(FinancialNotificationPort.class))
                    .isInstanceOf(NoopFinancialNotificationPort.class);
        });
    }

    @Configuration
    @ComponentScan(basePackageClasses = NoopDevBalanceInjector.class)
    static class FinancialFallbackConfiguration {
    }
}
