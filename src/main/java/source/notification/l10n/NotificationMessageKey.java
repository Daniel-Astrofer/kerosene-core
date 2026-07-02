package source.notification.l10n;

public enum NotificationMessageKey {
    ACCOUNT_CREATED("notification.account.created"),
    SECURITY_LOGIN_DETECTED("notification.security.login_detected"),
    SECURITY_ADMIN_ACCESS_ATTEMPT("notification.security.admin_access_attempt"),
    SECURITY_RECOVERY_COMPLETED("notification.security.recovery_completed"),
    INTERNAL_TRANSFER_RECEIVED("notification.transaction.internal.received"),
    INTERNAL_TRANSFER_SENT("notification.transaction.internal.sent"),
    PAYMENT_REQUEST_CREATED("notification.transaction.payment_request.created"),
    PAYMENT_REQUEST_PAID("notification.transaction.payment_request.paid"),
    TRANSACTION_BROADCAST_NO_AMOUNT("notification.transaction.broadcast.no_amount"),
    TRANSACTION_BROADCAST_WITH_AMOUNT("notification.transaction.broadcast.with_amount"),
    WALLET_ENTRY_DETECTED("notification.transaction.wallet_entry.detected"),
    WALLET_ENTRY_AMOUNT_DETECTED("notification.transaction.wallet_entry.amount_detected"),
    WALLET_ENTRY_AMOUNT_MESSAGE_DETECTED("notification.transaction.wallet_entry.amount_message_detected"),
    PENDING_DEPOSIT_DETECTED("notification.transaction.deposit.pending"),
    NETWORK_TRANSFER_CONFIRMED("notification.transaction.network_transfer.confirmed"),
    NETWORK_DEPOSIT_CONFIRMED("notification.transaction.network_deposit.confirmed"),
    EXTERNAL_ONCHAIN_PAYMENT_SENT("notification.transaction.external.onchain.payment_sent"),
    EXTERNAL_LIGHTNING_PAYMENT_SENT("notification.transaction.external.lightning.payment_sent"),
    EXTERNAL_ONCHAIN_DEPOSIT_CONFIRMED("notification.transaction.external.onchain.deposit_confirmed"),
    EXTERNAL_ONCHAIN_DEPOSIT_RECONCILED("notification.transaction.external.onchain.deposit_reconciled"),
    EXTERNAL_LIGHTNING_DEPOSIT_CONFIRMED("notification.transaction.external.lightning.deposit_confirmed"),
    EXTERNAL_LIGHTNING_DEPOSIT_RECONCILED("notification.transaction.external.lightning.deposit_reconciled");

    private final String baseKey;

    NotificationMessageKey(String baseKey) {
        this.baseKey = baseKey;
    }

    public String titleKey() {
        return baseKey + ".title";
    }

    public String bodyKey() {
        return baseKey + ".body";
    }
}
