package source.common.financial;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinancialFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(FinancialNotificationAuditPort.class)
    public FinancialNotificationAuditPort noopFinancialNotificationAuditPort() {
        return new NoopFinancialNotificationAuditPort();
    }

    @Bean
    @ConditionalOnMissingBean(FinancialNotificationPort.class)
    public FinancialNotificationPort noopFinancialNotificationPort() {
        return new NoopFinancialNotificationPort();
    }
}
