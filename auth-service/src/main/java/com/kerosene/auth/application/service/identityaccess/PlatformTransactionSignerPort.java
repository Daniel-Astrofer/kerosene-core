package com.kerosene.auth.application.service.identityaccess;

import com.kerosene.auth.model.entity.UserDataBase;

/** Port for platform co-signing required by selected advanced account-security modes. */
public interface PlatformTransactionSignerPort {

    /** Reports whether the platform signer is configured and available. */
    /** @return true when {@link #sign(UserDataBase)} can issue a signature */
    default boolean isAvailable() {
        return true;
    }

    /** Signs a platform transaction contribution for an authorized account. */
    /** @param user account whose platform share contributes to the transaction */
    /** @return platform signature material */
    String sign(UserDataBase user);
}
