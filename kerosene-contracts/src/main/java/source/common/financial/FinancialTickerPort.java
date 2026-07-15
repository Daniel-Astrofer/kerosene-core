package source.common.financial;

import java.math.BigDecimal;

public interface FinancialTickerPort {

    BigDecimal getPrice(String currency);
}
