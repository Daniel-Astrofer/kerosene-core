package source.common.financial;

import java.util.UUID;

public record FinancialDepositConfirmedNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID walletId,
        String rail,
        long creditedSats,
        int confirmations) {
}
