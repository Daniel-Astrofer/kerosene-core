package source.treasury.application.port.out;

import java.math.BigDecimal;

public interface LedgerLiabilityPort {

    BigDecimal loadTotalLiabilities();
}
