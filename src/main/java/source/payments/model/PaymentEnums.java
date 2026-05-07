package source.payments.model;

public final class PaymentEnums {

    private PaymentEnums() {
    }

    public enum PaymentRail {
        INTERNAL,
        LIGHTNING,
        ONCHAIN
    }

    public enum FeeMode {
        SENDER_PAYS,
        RECIPIENT_PAYS
    }

    public enum PaymentIntentStatus {
        CREATED,
        QUOTED,
        CONFIRMED,
        PROCESSING,
        ACCEPTED_BY_PROVIDER,
        REQUIRES_RECONCILIATION,
        SETTLED,
        FAILED,
        CANCELED,
        EXPIRED
    }

    public enum ReceivingMethodType {
        INTERNAL,
        LIGHTNING,
        ONCHAIN
    }

    public enum ReceivingMethodStatus {
        ACTIVE,
        INACTIVE,
        REVOKED,
        PENDING_VERIFICATION
    }

    public enum OnchainSpeed {
        ECONOMY,
        NORMAL,
        FAST
    }
}
