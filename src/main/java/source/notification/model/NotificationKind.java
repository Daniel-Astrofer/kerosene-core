package source.notification.model;

public enum NotificationKind {
    SYSTEM_INFO("system_info"),
    SECURITY_LOGIN_DETECTED("security_login_detected"),
    SECURITY_RECOVERY_COMPLETED("security_recovery_completed"),
    ACCOUNT_CREATED("account_created"),
    TRANSFER_RECEIVED("transfer_received"),
    TRANSFER_SENT("transfer_sent"),
    PAYMENT_REQUEST_CREATED("payment_request_created"),
    PAYMENT_REQUEST_PAID("payment_request_paid"),
    DEPOSIT_DETECTED("deposit_detected"),
    DEPOSIT_CONFIRMED("deposit_confirmed"),
    PAYMENT_SENT("payment_sent"),
    MINING_STARTED("mining_started"),
    MINING_COMPLETED("mining_completed"),
    MINING_CANCELLED("mining_cancelled");

    private final String wireValue;

    NotificationKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static NotificationKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            return SYSTEM_INFO;
        }

        for (NotificationKind kind : values()) {
            if (kind.wireValue.equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }

        return SYSTEM_INFO;
    }
}
