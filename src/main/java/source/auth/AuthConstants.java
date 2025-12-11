package source.auth;


public final class AuthConstants {

    private AuthConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }


    

    public static final int USERNAME_MAX_LENGTH = 50;


    public static final int PASSPHRASE_MAX_LENGTH = 161;


    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";

    // ==================== ERROR MESSAGES ====================

    public static final String ERR_USERNAME_NULL = "Username cannot be null or blank";
    public static final String ERR_USERNAME_INVALID_CHARS = "Username can only contain letters, numbers, and underscores";
    public static final String ERR_USERNAME_TOO_LONG = "Username exceeds maximum length of " + USERNAME_MAX_LENGTH + " characters";
    public static final String ERR_USERNAME_ALREADY_EXISTS = "Username is already taken";


    public static final String ERR_PASSPHRASE_NULL = "Passphrase cannot be null";
    public static final String ERR_PASSPHRASE_TOO_LONG = "Passphrase exceeds maximum length of " + PASSPHRASE_MAX_LENGTH + " characters";
    public static final String ERR_PASSPHRASE_INVALID_WORD = "Passphrase contains unrecognized BIP39 word";
    public static final String ERR_PASSPHRASE_INVALID_LENGTH = "Passphrase length is incompatible with BIP39 standard";
    public static final String ERR_PASSPHRASE_INVALID = "Invalid passphrase format";


    public static final String ERR_INVALID_CREDENTIALS = "Invalid username or passphrase";
    public static final String ERR_USER_NOT_FOUND = "User not found";
    public static final String ERR_DEVICE_NOT_RECOGNIZED = "Device not recognized. Please login from a recognized device";


    public static final String ERR_TOTP_INCORRECT = "Incorrect TOTP code";
    public static final String ERR_TOTP_EXPIRED = "TOTP verification time has expired. Please sign up again";


    public static final String MSG_LOGIN_SUCCESS = "Login successful";
    public static final String MSG_SIGNUP_SUCCESS = "Account created successfully";
    public static final String MSG_TOTP_VERIFIED = "TOTP verified successfully";

    // ==================== CONFIGURATION ====================
    

    public static final String APP_NAME = "Kerosene";

    public static final String TOTP_URI_FORMAT = "otpauth://totp/%s:%s?secret=%s&issuer=%s";
}
