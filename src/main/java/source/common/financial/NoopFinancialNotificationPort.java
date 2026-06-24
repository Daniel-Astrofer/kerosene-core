package source.common.financial;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnMissingBean(FinancialNotificationPort.class)
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

    @Override
    public void notifyDemoBalanceCredited(Long userId, UUID walletId, String walletName, String amountBtc) {
        // Intentionally empty. Notification service provides the runtime adapter when present.
    }
}
