package com.kerosene.common.financial;

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

    @Override
    public void notifyDepositDetected(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        // Intentionally empty. Notification service provides the runtime adapter when present.
    }

    @Override
    public void notifyDepositConfirmationProgress(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        // Intentionally empty. Notification service provides the runtime adapter when present.
    }

    @Override
    public void notifyOutboundDetected(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations,
            String destinationHint) {
        // Intentionally empty.
    }

    @Override
    public void notifyOutboundConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations) {
        // Intentionally empty.
    }

    @Override
    public void notifyInternalTransferReceived(
            Long receiverUserId,
            UUID transactionId,
            UUID walletId,
            long amountSats) {
        // Intentionally empty.
    }

    @Override
    public void notifyInternalTransferSent(
            Long senderUserId,
            UUID transactionId,
            UUID walletId,
            long amountSats) {
        // Intentionally empty.
    }

    @Override
    public void notifyExternalPaymentSent(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats) {
        // Intentionally empty.
    }
}
