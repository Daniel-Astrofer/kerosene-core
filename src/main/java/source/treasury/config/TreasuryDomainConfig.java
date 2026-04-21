package source.treasury.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import source.treasury.domain.service.FeeMarkupPolicy;
import source.treasury.domain.service.LiquidityRebalancePolicy;

@Configuration
public class TreasuryDomainConfig {

    @Bean
    public FeeMarkupPolicy feeMarkupPolicy() {
        return new FeeMarkupPolicy();
    }

    @Bean
    public LiquidityRebalancePolicy liquidityRebalancePolicy() {
        return new LiquidityRebalancePolicy();
    }
}
