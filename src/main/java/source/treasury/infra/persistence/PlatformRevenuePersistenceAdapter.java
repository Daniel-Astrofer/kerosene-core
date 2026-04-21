package source.treasury.infra.persistence;

import org.springframework.stereotype.Component;
import source.treasury.application.port.out.RevenuePersistencePort;
import source.treasury.entity.PlatformRevenue;
import source.treasury.repository.PlatformRevenueRepository;

import java.math.BigDecimal;

@Component
public class PlatformRevenuePersistenceAdapter implements RevenuePersistencePort {

    private final PlatformRevenueRepository platformRevenueRepository;

    public PlatformRevenuePersistenceAdapter(PlatformRevenueRepository platformRevenueRepository) {
        this.platformRevenueRepository = platformRevenueRepository;
    }

    @Override
    public BigDecimal accumulateProfit(BigDecimal profitBtc) {
        PlatformRevenue revenue = platformRevenueRepository.getGlobalRevenue()
                .orElseGet(this::newGlobalRevenue);

        BigDecimal currentProfit = revenue.getAccumulatedProfit() != null
                ? revenue.getAccumulatedProfit()
                : BigDecimal.ZERO;
        BigDecimal updatedProfit = currentProfit.add(profitBtc);
        revenue.setAccumulatedProfit(updatedProfit);

        platformRevenueRepository.save(revenue);
        return updatedProfit;
    }

    private PlatformRevenue newGlobalRevenue() {
        PlatformRevenue revenue = new PlatformRevenue();
        revenue.setId(1L);
        return revenue;
    }
}
