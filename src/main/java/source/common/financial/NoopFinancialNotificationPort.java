package source.common.financial;

import java.util.UUID;

public class NoopFinancialNotificationPort implements FinancialNotificationPort {

    @Override
    public void notifyDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        // Intentionally empty. Notification service provides the runtime adapter when present.
    }

    @Override
    public void notifyPaymentRequestDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID paymentRequestId,
            String publicId,
            UUID walletId,
            String rail,
            long creditedSats) {
        // Intentionally empty. Notification service provides the runtime adapter when present.
    }
}
