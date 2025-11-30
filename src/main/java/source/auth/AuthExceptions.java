package source.auth;

public class AuthExceptions {


    public static class AuthValidationException extends RuntimeException {
        public AuthValidationException(String message)   {
            super(message);
        }
    }

    public static class UserAlreadyExistsException extends AuthValidationException {
        public UserAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class UsernameCantBeNull extends AuthValidationException {
        public UsernameCantBeNull(String message) {
            super(message);
        }
    }

    public static class PassphraseCantBeNull extends AuthValidationException {
        public PassphraseCantBeNull(String message) {
            super(message);
        }
    }

    public static class InvalidCharacterUsername extends AuthValidationException {
        public InvalidCharacterUsername(String message) {
            super(message);
        }
    }

    public static class UsernameCharacterLimitException extends AuthValidationException {
        public UsernameCharacterLimitException(String message) {
            super(message);
        }
    }

    public static class UserNoExists extends AuthValidationException {
        public UserNoExists(String message) {
            super(message);
        }
    }

    public static class InvalidPassphrase extends AuthValidationException {
        public InvalidPassphrase(String message) {
            super(message);
        }
    }

    public static class incorrectTotp extends AuthValidationException {
        public incorrectTotp(String message) {
            super(message);
        }
    }

    public static class InvalidCredentials extends AuthValidationException {
        public InvalidCredentials(String message) {
            super(message);
        }
    }

    public static class UnrrecognizedDevice extends AuthValidationException {
        public UnrrecognizedDevice(String message) {
            super(message);
        }
    }

    public static class TotpTimeExceded extends AuthValidationException {
        public TotpTimeExceded(String message) {
            super(message);
        }
    }


}
