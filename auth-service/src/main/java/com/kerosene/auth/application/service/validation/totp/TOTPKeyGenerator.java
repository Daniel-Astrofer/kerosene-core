package com.kerosene.auth.application.service.validation.totp;

import com.kerosene.auth.application.service.validation.totp.contracts.TOTPKeyGenerate;
import org.jboss.aerogear.security.otp.api.Base32;
import org.springframework.stereotype.Component;

/** Creates random Base32 secrets for TOTP enrollment. */
@Component
public class TOTPKeyGenerator implements TOTPKeyGenerate {

    /**
     * Generates a cryptographically random Base32 shared secret.
     * @return secret to provision in the user's authenticator
     */
    @Override
    public String keyGenerator() {
        return Base32.random();
    }
}
