package com.kerosene.auth.application.service.validation.totp.contracts;


/** Contract for producing shared secrets used to enroll TOTP authenticators. */
public interface TOTPKeyGenerate {
    /** Generates a random Base32 secret. */
    /** @return new secret suitable for authenticator provisioning */
    String keyGenerator();
}
