package source.auth.application.service.authentication.signup;

public record SignupValidationContext(String username, char[] passphrase) {
}
