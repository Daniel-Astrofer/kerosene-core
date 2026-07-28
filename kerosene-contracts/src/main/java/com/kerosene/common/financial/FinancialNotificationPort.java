package com.kerosene.common.financial;

import java.util.UUID;

/**
 * @deprecated All methods are replaced by {@link FinancialNotificationPortV2#publish(FinancialNotificationPortV2.FinancialNotificationEvent)}.
 *             Implementations MUST migrate to the V2 typed event contract.
 *             Default no-op bodies are removed; every deprecated method now throws
 *             {@link UnsupportedOperationException} to prevent silent event loss.
 */
@Deprecated
public interface FinancialNotificationPort {

    @Deprecated
    default void notifyDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(DepositConfirmed) instead.");
    }

    @Deprecated
    default void notifyPaymentRequestDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID paymentRequestId,
            String publicId,
            UUID walletId,
            String rail,
            long creditedSats) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(DepositConfirmed) instead.");
    }

    @Deprecated
    default void notifyDepositDetected(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(DepositDetected) instead.");
    }

    @Deprecated
    default void notifyDepositConfirmationProgress(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(DepositConfirmationProgress) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(DepositDropped) instead.
     */
    @Deprecated
    default void notifyDepositDropped(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(DepositDropped) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(PaymentBroadcast) or PaymentConfirmed instead.
     */
    @Deprecated
    default void notifyOutboundDetected(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations,
            String destinationHint) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(PaymentBroadcast) or PaymentConfirmed instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(PaymentConfirmed) instead.
     */
    @Deprecated
    default void notifyOutboundConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(PaymentConfirmed) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish with appropriate event type instead.
     */
    @Deprecated
    default void notifyInternalTransferReceived(
            Long receiverUserId,
            UUID transactionId,
            UUID walletId,
            long amountSats) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish() with appropriate event type instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish with appropriate event type instead.
     */
    @Deprecated
    default void notifyInternalTransferSent(
            Long senderUserId,
            UUID transactionId,
            UUID walletId,
            long amountSats) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish() with appropriate event type instead.");
    }

    /**
     * @deprecated Ambiguous: "sent" could mean initiated, broadcast, or confirmed.
     *             Use FinancialNotificationPortV2.PaymentInitiated, PaymentBroadcast, or PaymentConfirmed instead.
     */
    @Deprecated
    default void notifyExternalPaymentSent(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats) {
        throw new UnsupportedOperationException(
            "Ambiguous: 'sent' could mean initiated, broadcast, or confirmed. "
            + "Use FinancialNotificationPortV2.publish(PaymentInitiated), publish(PaymentBroadcast), "
            + "or publish(PaymentConfirmed) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(PaymentInitiated) instead.
     */
    @Deprecated
    default void notifyPaymentInitiated(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(PaymentInitiated) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(PaymentBroadcast) instead.
     */
    @Deprecated
    default void notifyPaymentBroadcast(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            String txid) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(PaymentBroadcast) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(PaymentConfirmed) instead.
     */
    @Deprecated
    default void notifyPaymentConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(PaymentConfirmed) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(PaymentFailed) instead.
     */
    @Deprecated
    default void notifyPaymentFailed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            String failureCode,
            String failureMessage) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(PaymentFailed) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(ReconciliationRequired) instead.
     */
    @Deprecated
    default void notifyPaymentReconciliationRequired(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            String reason) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(ReconciliationRequired) instead.");
    }

    /**
     * @deprecated Use FinancialNotificationPortV2.publish(PaymentConflicted) instead.
     */
    @Deprecated
    default void notifyOutboundConflicted(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            String txid) {
        throw new UnsupportedOperationException(
            "Use FinancialNotificationPortV2.publish(PaymentConflicted) instead.");
    }
}
