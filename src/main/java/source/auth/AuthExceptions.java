package source.auth;

/**
 * Centralized exception classes for authentication and authorization operations.
 * All exceptions extend AuthValidationException for consistent error handling.
 */
public class AuthExceptions {

    /**
     * Base exception for all authentication validation errors.
     */
    public static class AuthValidationException extends RuntimeException {
        public AuthValidationException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when attempting to create a user that already exists.
     */
    public static class UserAlreadyExistsException extends AuthValidationException {
        public UserAlreadyExistsException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when username is null or blank.
     */
    public static class UsernameCantBeNull extends AuthValidationException {
        public UsernameCantBeNull(String message) {
            super(message);
        }
    }

    /**
     * Thrown when passphrase is null.
     */
    public static class PassphraseCantBeNull extends AuthValidationException {
        public PassphraseCantBeNull(String message) {
            super(message);
        }
    }

    /**
     * Thrown when username contains invalid characters.
     */
    public static class InvalidCharacterUsername extends AuthValidationException {
        public InvalidCharacterUsername(String message) {
            super(message);
        }
    }

    /**
     * Thrown when username or passphrase exceeds character limit.
     */
    public static class CharacterLimitException extends AuthValidationException {
        public CharacterLimitException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when user does not exist in the database.
     */
    public static class UserNotFoundException extends AuthValidationException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when passphrase is invalid or doesn't meet BIP39 requirements.
     */
    public static class InvalidPassphrase extends AuthValidationException {
        public InvalidPassphrase(String message) {
            super(message);
        }
    }

    /**
     * Thrown when TOTP code is incorrect.
     */
    public static class IncorrectTotpException extends AuthValidationException {
        public IncorrectTotpException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when login credentials are invalid.
     */
    public static class InvalidCredentials extends AuthValidationException {
        public InvalidCredentials(String message) {
            super(message);
        }
    }

    /**
     * Thrown when device is not recognized.
     */
    public static class UnrecognizedDeviceException extends AuthValidationException {
        public UnrecognizedDeviceException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when TOTP verification time window has exceeded.
     */
    public static class TotpTimeExceededException extends AuthValidationException {
        public TotpTimeExceededException(String message) {
            super(message);
        }
    }

    // Deprecated exceptions - mantidos para compatibilidade, serão removidos em versão futura
    /**
     * @deprecated Use {@link CharacterLimitException} instead
     */
    @Deprecated
    public static class UsernameCharacterLimitException extends CharacterLimitException {
        public UsernameCharacterLimitException(String message) {
            super(message);
        }
    }

    /**
     * @deprecated Use {@link UserNotFoundException} instead
     */
    @Deprecated
    public static class UserNoExists extends UserNotFoundException {
        public UserNoExists(String message) {
            super(message);
        }
    }

    /**
     * @deprecated Use {@link IncorrectTotpException} instead
     */
    @Deprecated
    public static class incorrectTotp extends IncorrectTotpException {
        public incorrectTotp(String message) {
            super(message);
        }
    }

    /**
     * @deprecated Use {@link UnrecognizedDeviceException} instead
     */
    @Deprecated
    public static class UnrrecognizedDevice extends UnrecognizedDeviceException {
        public UnrrecognizedDevice(String message) {
            super(message);
        }
    }

    /**
     * @deprecated Use {@link TotpTimeExceededException} instead
     */
    @Deprecated
    public static class TotpTimeExceded extends TotpTimeExceededException {
        public TotpTimeExceded(String message) {
            super(message);
        }
    }
}
