package source.common.financial;

import java.util.UUID;

public record FinancialPaymentRequestDepositConfirmedNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID paymentRequestId,
        String publicId,
        UUID walletId,
        String rail,
        long creditedSats) {
}
