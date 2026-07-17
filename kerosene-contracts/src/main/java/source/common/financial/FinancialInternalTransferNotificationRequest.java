package source.common.financial;

import java.util.UUID;

public record FinancialInternalTransferNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID walletId,
        long amountSats) {
}
