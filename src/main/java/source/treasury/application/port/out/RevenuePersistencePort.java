package source.treasury.application.port.out;

import java.math.BigDecimal;

public interface RevenuePersistencePort {

    BigDecimal accumulateProfit(BigDecimal profitBtc);
}
