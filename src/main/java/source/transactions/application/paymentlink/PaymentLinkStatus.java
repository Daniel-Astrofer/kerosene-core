package source.transactions.application.paymentlink;

public final class PaymentLinkStatus {

    public static final String PENDING = "pending";
    public static final String PAID = "paid";
    public static final String EXPIRED = "expired";
    public static final String COMPLETED = "completed";
    public static final String VERIFYING_ONBOARDING = "verifying_onboarding";

    private PaymentLinkStatus() {
    }
}
