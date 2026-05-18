package source.treasury.domain.model;

import java.math.BigDecimal;

public record MonitoredWallet(
        Long id,
        Long userId,
        String name,
        String address,
        BigDecimal balanceBtc
) {
}
