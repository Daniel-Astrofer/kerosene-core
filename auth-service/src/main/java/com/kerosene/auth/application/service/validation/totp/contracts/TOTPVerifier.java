package com.kerosene.auth.application.service.validation.totp.contracts;

import javax.crypto.SecretKey;

/** Contract for decrypting TOTP enrollment data and validating authenticator codes. */
public interface TOTPVerifier {

    /** Checks whether a code matches a supported time window. */
    /** @param totpSecret Base32 shared secret */
    /** @param code candidate six-digit code */
    /** @return whether the candidate matches */
    boolean totpMatcher(String totpSecret, String code);

    /** Verifies a code against a secret and reports authentication failure by exception. */
    /** @param totpSecret encrypted or plaintext secret */
    /** @param code candidate six-digit code */
    void totpVerify(String totpSecret, String code);

    /** Converts encrypted or legacy plaintext secret storage into Base32 form. */
    /** @param totpSecret stored secret representation */
    /** @param secretKey decryption key */
    /** @return decoded Base32 secret */
    String totpDecryptedToString(String totpSecret, SecretKey secretKey);
}
