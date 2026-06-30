package source.common.financial;

import java.util.UUID;

public record FinancialDemoBalanceCreditedNotificationRequest(
        Long userId,
        UUID walletId,
        String walletName,
        String amountBtc) {
}
