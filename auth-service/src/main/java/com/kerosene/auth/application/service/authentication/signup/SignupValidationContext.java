package com.kerosene.auth.application.service.authentication.signup;

/**
 * Immutable request values shared by ordered signup validation handlers.
 * @param username candidate username
 * @param passphrase mutable passphrase characters supplied for policy checks
 */
public record SignupValidationContext(String username, char[] passphrase) {
}
