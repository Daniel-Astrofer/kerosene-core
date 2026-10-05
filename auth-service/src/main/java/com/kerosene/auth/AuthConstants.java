package com.kerosene.auth;


/** Central, immutable validation, user-message, application, and JWT duration constants. */
public final class AuthConstants {

    /** Prevents construction because every member is a static constant. */
    private AuthConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }




    /** Maximum accepted username length in characters. */
    public static final int USERNAME_MAX_LENGTH = 50;

    /** Maximum accepted password length in characters. */
    public static final int PASSWORD_MAX_LENGTH = 128;
    /** Minimum accepted password length in characters. */
    public static final int PASSWORD_MIN_LENGTH = 12;

    /** Regular expression restricting usernames to ASCII letters, digits, and underscore. */
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";

    // ==================== ERROR MESSAGES ====================

    /** Validation message used when a username is null or blank. */
    public static final String ERR_USERNAME_NULL = "Username cannot be null or blank";
    /** Validation message used when a username contains unsupported characters. */
    public static final String ERR_USERNAME_INVALID_CHARS = "Username can only contain letters, numbers, and underscores";
    /** Validation message used when a username exceeds {@link #USERNAME_MAX_LENGTH}. */
    public static final String ERR_USERNAME_TOO_LONG = "Username exceeds maximum length of " + USERNAME_MAX_LENGTH + " characters";
    /** Conflict message used when a username is already registered. */
    public static final String ERR_USERNAME_ALREADY_EXISTS = "Username is already taken";

    /** Validation message used when a password is null. */
    public static final String ERR_PASSWORD_NULL = "Password cannot be null";
    /** Validation message used when a password exceeds {@link #PASSWORD_MAX_LENGTH}. */
    public static final String ERR_PASSWORD_TOO_LONG = "Password exceeds maximum length of " + PASSWORD_MAX_LENGTH + " characters";
    /** Validation message used when a password is shorter than {@link #PASSWORD_MIN_LENGTH}. */
    public static final String ERR_PASSWORD_TOO_SHORT = "Password must have at least " + PASSWORD_MIN_LENGTH + " characters";
    /** Validation message used when a password fails the configured character-class policy. */
    public static final String ERR_PASSWORD_WEAK = "Password must include upper, lower, number, and symbol characters";

    /** Generic authentication failure message that does not distinguish user absence from bad credentials. */
    public static final String ERR_INVALID_CREDENTIALS = "Invalid username or password";
    /** Lookup failure message used when a referenced user does not exist. */
    public static final String ERR_USER_NOT_FOUND = "User not found";
    /** Message requesting login from a recognized device. */
    public static final String ERR_DEVICE_NOT_RECOGNIZED = "Device not recognized. Please login from a recognized device";

    /** Message used when a submitted TOTP code does not match. */
    public static final String ERR_TOTP_INCORRECT = "Incorrect TOTP code";
    /** Message used when the TOTP enrollment/verification window has expired. */
    public static final String ERR_TOTP_EXPIRED = "TOTP verification time has expired. Please sign up again";

    /** User-facing success text for login. */
    public static final String MSG_LOGIN_SUCCESS = "Login successful";
    /** User-facing success text for account creation. */
    public static final String MSG_SIGNUP_SUCCESS = "Account created successfully";
    /** User-facing success text for TOTP verification. */
    public static final String MSG_TOTP_VERIFIED = "TOTP verified successfully";

    // ==================== CONFIGURATION ====================

    /** Product name embedded in application-generated labels and authenticator metadata. */
    public static final String APP_NAME = "Kerosene";

    /** URI template used to provision a TOTP authenticator entry. */
    public static final String TOTP_URI_FORMAT = "otpauth://totp/%s:%s?secret=%s&issuer=%s";

    // ==================== JWT CONFIGURATION ====================

    /** JWT lifetime in milliseconds (24 hours). */
    public static final long JWT_EXPIRATION_TIME = 86400000L; // 24 * 60 * 60 * 1000

    /** Remaining JWT lifetime in milliseconds below which renewal is recommended (one hour). */
    public static final long JWT_RENEWAL_THRESHOLD = 3600000L; // 1 * 60 * 60 * 1000
}
