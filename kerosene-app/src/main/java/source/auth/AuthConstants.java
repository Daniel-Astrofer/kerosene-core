package source.auth;


public final class AuthConstants {

    private AuthConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }


    

    public static final int USERNAME_MAX_LENGTH = 50;


    public static final int PASSWORD_MAX_LENGTH = 128;
    public static final int PASSWORD_MIN_LENGTH = 12;


    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";

    // ==================== ERROR MESSAGES ====================

    public static final String ERR_USERNAME_NULL = "Username cannot be null or blank";
    public static final String ERR_USERNAME_INVALID_CHARS = "Username can only contain letters, numbers, and underscores";
    public static final String ERR_USERNAME_TOO_LONG = "Username exceeds maximum length of " + USERNAME_MAX_LENGTH + " characters";
    public static final String ERR_USERNAME_ALREADY_EXISTS = "Username is already taken";


    public static final String ERR_PASSWORD_NULL = "Password cannot be null";
    public static final String ERR_PASSWORD_TOO_LONG = "Password exceeds maximum length of " + PASSWORD_MAX_LENGTH + " characters";
    public static final String ERR_PASSWORD_TOO_SHORT = "Password must have at least " + PASSWORD_MIN_LENGTH + " characters";
    public static final String ERR_PASSWORD_WEAK = "Password must include upper, lower, number, and symbol characters";


    public static final String ERR_INVALID_CREDENTIALS = "Invalid username or password";
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

    // ==================== JWT CONFIGURATION ====================
    
    // JWT Token expiration in milliseconds (24 hours)
    public static final long JWT_EXPIRATION_TIME = 86400000L; // 24 * 60 * 60 * 1000
    
    // JWT Token renewal time check (renew if less than 1 hour remaining)
    public static final long JWT_RENEWAL_THRESHOLD = 3600000L; // 1 * 60 * 60 * 1000
}
