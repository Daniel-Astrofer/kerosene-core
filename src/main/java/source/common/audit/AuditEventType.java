package source.common.audit;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stable domain audit taxonomy for security, operator and financial events.
 *
 * <p>Audit event names are persisted and consumed by incident-response tooling, so
 * changes must be additive. Unknown names are rejected instead of being truncated
 * or normalized into a different event.
 */
public enum AuditEventType {
    AUTH_LOGIN_SUCCEEDED,
    AUTH_LOGIN_FAILED,
    AUTH_LOGOUT,
    JWT_SESSION_REVOKED,
    ADMIN_ACCESS_REQUESTED,
    ADMIN_ACCESS_APPROVED,
    ADMIN_ACCESS_REJECTED,
    ADMIN_ACCESS_REDEEMED,
    BACKUP_CODES_REGENERATED,
    KFE_WALLET_CREATED,
    KFE_TRANSACTION_SUBMITTED,
    KFE_IDEMPOTENCY_CONFLICT,
    KFE_OUTBOX_DISPATCHED,
    KFE_OUTBOX_RETRY,
    KFE_SETTLEMENT_COMPLETED,
    KFE_SETTLEMENT_FAILED,
    KFE_INBOUND_CREDITED,
    KFE_INBOUND_DUPLICATE_REJECTED,
    VAULT_ATTESTATION_SUCCEEDED,
    VAULT_ATTESTATION_FAILED,
    MPC_SIGN_REJECTED,
    MPC_UNSUPPORTED_MODE_REJECTED,

    KFE_WALLET_ARCHIVED,
    KFE_WALLET_ADDRESS_ROTATED,
    KFE_WALLET_BALANCE_ADJUSTED,
    KFE_PAYMENT_REQUEST_CREATED,
    KFE_DEV_DEPOSIT_INSTANT_CREDITED,
    KFE_PAYMENT_REQUEST_EXPIRED,
    KFE_TRANSACTION_SETTLED,
    KFE_TRANSACTION_FAILED,
    KFE_EXECUTION_DISPATCHED,
    KFE_EXECUTION_RETRYABLE_FAILURE,
    KFE_EXECUTION_FINAL_FAILURE,
    KFE_TRANSACTION_REQUIRES_RECONCILIATION,
    KFE_INBOUND_SETTLED,
    KFE_PAYMENT_REQUEST_CANCELLED,
    KFE_PAYMENT_REQUEST_HIDDEN,
    KFE_PSBT_CREATED,
    KFE_PSBT_SIGNED,
    KFE_PSBT_REJECTED,
    KFE_PSBT_WORKFLOW_CREATED,
    KFE_PSBT_WORKFLOW_SIGNED,
    KFE_PSBT_WORKFLOW_BROADCAST,
    KFE_COLD_WALLET_PSBT_CREATED,
    KFE_WALLET_CREATE_FAILED,
    KFE_WALLET_STATUS_RESTORED,
    KFE_WALLET_UPDATED;

    private static final int MAX_NAME_LENGTH = 96;
    private static final Map<String, AuditEventType> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(AuditEventType::name, Function.identity()));

    public static AuditEventType requireKnown(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Audit event type is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        AuditEventType eventType = BY_NAME.get(normalized);
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown audit event type");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Audit event type is too long");
        }
        return eventType;
    }
}
