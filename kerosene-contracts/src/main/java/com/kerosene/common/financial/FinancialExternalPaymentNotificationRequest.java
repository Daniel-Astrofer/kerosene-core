package source.common.financial;

import java.util.UUID;

public record FinancialExternalPaymentNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID walletId,
        String rail,
        long amountSats) {
}
