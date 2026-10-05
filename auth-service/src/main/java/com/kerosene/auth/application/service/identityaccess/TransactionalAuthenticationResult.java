package com.kerosene.auth.application.service.identityaccess;

import com.kerosene.auth.model.entity.UserDataBase;

/**
 * Result of transactional factor authorization.
 * @param user resolved account whose factors were checked
 * @param platformSignature co-signature or empty string when scope/mode does not require one
 */
public record TransactionalAuthenticationResult(
        UserDataBase user,
        String platformSignature) {

    /** Normalizes absent platform signatures to the empty-string contract. */
    public TransactionalAuthenticationResult {
        platformSignature = platformSignature != null ? platformSignature : "";
    }
}
