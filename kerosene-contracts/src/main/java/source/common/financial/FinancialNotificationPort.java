package source.common.financial;

import java.util.UUID;

public interface FinancialNotificationPort {

    void notifyDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations);

    void notifyPaymentRequestDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID paymentRequestId,
            String publicId,
            UUID walletId,
            String rail,
            long creditedSats);
}
