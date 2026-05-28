package source.ledger.application.balance;

import java.math.BigDecimal;

public record LedgerBalanceUpdate(
        Long walletId,
        String walletName,
        Long userId,
        BigDecimal newBalance,
        BigDecimal amount,
        String context) {
}
