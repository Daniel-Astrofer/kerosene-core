package com.kerosene.auth.application.service.identityaccess;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.kerosene.auth.model.entity.UserDataBase;

/** Non-production signer that advertises unavailability and rejects signing attempts. */
@Component
@Profile("!prod")
public class UnavailablePlatformTransactionSigner implements PlatformTransactionSignerPort {

    /** Always reports unavailable so advanced outbound authorization fails closed outside production. */
    @Override
    public boolean isAvailable() {
        return false;
    }

    /** Rejects all signing requests because this profile intentionally has no platform co-signer. */
    /** @param user account associated with the requested signature */
    /** @throws IllegalStateException because platform signing is not configured */
    @Override
    public String sign(UserDataBase user) {
        throw new IllegalStateException("Platform transaction co-signing is not configured.");
    }
}
