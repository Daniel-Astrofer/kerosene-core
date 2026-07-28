package com.kerosene.common.financial;

import java.time.Instant;

/**
 * V2 notification port — single typed event method.
 * Every event is mandatory; no silent no-ops.
 */
public interface FinancialNotificationPortV2 {

    DeliveryReceipt publish(FinancialNotificationEvent event);

    record DeliveryReceipt(
        String eventId,       // idempotency key
        boolean delivered,    // was it accepted by the subscriber
        String reasonCode     // why delivery succeeded or failed
    ) {
        public DeliveryReceipt {
            if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
            if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode required");
        }
    }

    /**
     * Sealed hierarchy of financial events. Every event carries:
     * - eventId for idempotency
     * - transactionId for correlation
     * - eventSequence for ordering
     * - occurredAt for timeline
     * - schemaVersion for compatibility
     */
    sealed interface FinancialNotificationEvent
        permits FinancialNotificationEvent.DepositDetected,
                FinancialNotificationEvent.DepositConfirmed,
                FinancialNotificationEvent.DepositConfirmationProgress,
                FinancialNotificationEvent.DepositDropped,
                FinancialNotificationEvent.PaymentInitiated,
                FinancialNotificationEvent.PaymentBroadcast,
                FinancialNotificationEvent.PaymentConfirmed,
                FinancialNotificationEvent.PaymentFailed,
                FinancialNotificationEvent.PaymentConflicted,
                FinancialNotificationEvent.ReconciliationRequired {

        /** Unique idempotency key for this specific event */
        String eventId();
        /** The associated transaction */
        String transactionId();
        /** Monotonic sequence number for this transaction's events */
        long eventSequence();
        /** When the event occurred (wall clock) */
        Instant occurredAt();
        /** Schema version for forward compatibility */
        int schemaVersion();
        /** Correlation ID linking related events */
        String correlationId();

        // --- Deposit events ---

        record DepositDetected(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, long amountSats, String txid, String address
        ) implements FinancialNotificationEvent {
            public DepositDetected {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("transactionId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (rail == null || rail.isBlank()) throw new IllegalArgumentException("rail required");
                if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0");
                if (txid == null || txid.isBlank()) throw new IllegalArgumentException("txid required");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record DepositConfirmed(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, long amountSats, String txid, int confirmations
        ) implements FinancialNotificationEvent {
            public DepositConfirmed {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("transactionId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0");
                if (confirmations < 0) throw new IllegalArgumentException("confirmations must be >= 0");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record DepositConfirmationProgress(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, long amountSats, String txid, int confirmations, int requiredConfirmations
        ) implements FinancialNotificationEvent {
            public DepositConfirmationProgress {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (confirmations < 0) throw new IllegalArgumentException("confirmations must be >= 0");
                if (requiredConfirmations < 1) throw new IllegalArgumentException("requiredConfirmations must be >= 1");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record DepositDropped(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String txid, String reasonCode, String reasonDetail
        ) implements FinancialNotificationEvent {
            public DepositDropped {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode required");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        // --- Payment events ---

        record PaymentInitiated(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, long amountSats, long feeSats, String destinationHash
        ) implements FinancialNotificationEvent {
            public PaymentInitiated {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record PaymentBroadcast(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, String txid
        ) implements FinancialNotificationEvent {
            public PaymentBroadcast {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (txid == null || txid.isBlank()) throw new IllegalArgumentException("txid required");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record PaymentConfirmed(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, String txid, int confirmations
        ) implements FinancialNotificationEvent {
            public PaymentConfirmed {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (txid == null || txid.isBlank()) throw new IllegalArgumentException("txid required");
                if (confirmations < 0) throw new IllegalArgumentException("confirmations must be >= 0");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record PaymentFailed(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, String reasonCode, String reasonDetail
        ) implements FinancialNotificationEvent {
            public PaymentFailed {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode required");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record PaymentConflicted(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String rail, String txid, String conflictingTxid
        ) implements FinancialNotificationEvent {
            public PaymentConflicted {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (txid == null || txid.isBlank()) throw new IllegalArgumentException("txid required");
                if (conflictingTxid == null || conflictingTxid.isBlank()) throw new IllegalArgumentException("conflictingTxid required");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }

        record ReconciliationRequired(
            String eventId, String transactionId, long eventSequence, Instant occurredAt, int schemaVersion, String correlationId,
            String walletId, String reasonCode, String reasonDetail
        ) implements FinancialNotificationEvent {
            public ReconciliationRequired {
                if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId required");
                if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
                if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode required");
                if (occurredAt == null) throw new IllegalArgumentException("occurredAt required");
            }
        }
    }
}
