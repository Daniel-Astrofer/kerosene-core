package source.common.exception;

/**
 * Standardized Error Codes for the Kerosene Platform.
 * Used by the Flutter frontend to identify specific error states.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // Authentication Errors
    public static final String AUTH_USER_ALREADY_EXISTS = "AUTH_001";
    public static final String AUTH_USERNAME_NULL = "AUTH_002";
    public static final String AUTH_PASSPHRASE_NULL = "AUTH_003";
    public static final String AUTH_INVALID_USERNAME_CHAR = "AUTH_004";
    public static final String AUTH_CHARACTER_LIMIT = "AUTH_005";
    public static final String AUTH_USER_NOT_FOUND = "AUTH_006";
    public static final String AUTH_INVALID_PASSPHRASE = "AUTH_007";
    public static final String AUTH_INCORRECT_TOTP = "AUTH_008";
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_009";
    public static final String AUTH_UNRECOGNIZED_DEVICE = "AUTH_010";
    public static final String AUTH_TOTP_TIMEOUT = "AUTH_011";
    public static final String AUTH_PASSKEY_CHALLENGE = "AUTH_012";
    public static final String AUTH_SESSION_EXPIRED = "AUTH_013";
    public static final String AUTH_PASSKEY_LINK_REQUIRED = "AUTH_014";
    public static final String AUTH_PASSKEY_ASSERTION_FAILED = "AUTH_015";
    public static final String AUTH_PASSKEY_REPLAY = "AUTH_016";
    public static final String AUTH_PASSKEY_CREDENTIAL_NOT_FOUND = "AUTH_017";
    public static final String AUTH_APP_PIN_NOT_CONFIGURED = "AUTH_018";
    public static final String AUTH_APP_PIN_INVALID = "AUTH_019";
    public static final String AUTH_APP_PIN_LOCKED = "AUTH_020";
    public static final String AUTH_APP_PIN_DEVICE_REQUIRED = "AUTH_021";
    public static final String AUTH_PASSKEY_INVALID_ORIGIN = "AUTH_022";
    public static final String AUTH_TRANSACTIONAL_AUTH_REQUIRED = "AUTH_023";
    public static final String AUTH_GENERIC = "AUTH_099";

    // Ledger Errors
    public static final String LEDGER_NOT_FOUND = "LEDGER_001";
    public static final String LEDGER_RECEIVER_NOT_FOUND = "LEDGER_002";
    public static final String LEDGER_ALREADY_EXISTS = "LEDGER_003";
    public static final String LEDGER_INSUFFICIENT_BALANCE = "LEDGER_004";
    public static final String LEDGER_INVALID_OPERATION = "LEDGER_005";
    public static final String LEDGER_PAYMENT_NOT_FOUND = "LEDGER_006";
    public static final String LEDGER_PAYMENT_EXPIRED = "LEDGER_007";
    public static final String LEDGER_PAYMENT_ALREADY_PAID = "LEDGER_008";
    public static final String LEDGER_PAYMENT_SELF_PAY = "LEDGER_009";
    public static final String LEDGER_GENERIC = "LEDGER_099";

    // Wallet Errors
    public static final String WALLET_NAME_EXISTS = "WALLET_001";
    public static final String WALLET_NOT_FOUND = "WALLET_002";
    public static final String WALLET_GENERIC = "WALLET_099";

    // Hydra / Quorum Errors
    public static final String HYDRA_QUORUM_TIMEOUT = "HYDRA_001";
    public static final String VAULT_STORAGE_ERROR = "VAULT_001";
    public static final String KRS_BUSINESS_ERROR = "KRS_099";

    // System / Infrastructure Errors
    public static final String SYS_INVALID_ARGUMENTS = "SYS_001";
    public static final String SYS_METHOD_NOT_ALLOWED = "SYS_002";
    public static final String SYS_NOT_FOUND = "SYS_404";
    public static final String SYS_INTERNAL_ERROR = "SYS_500";
}
