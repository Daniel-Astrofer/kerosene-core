package source.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletCardProfile(
        Long userId,
        String tier,
        BigDecimal balanceBtc,
        LocalDateTime updatedAt
) {
}
