package com.kerosene.auth.application.port.out;

import java.util.List;

import com.kerosene.auth.model.entity.PasskeyCredential;

/** Outbound persistence port for account-owned WebAuthn passkey credentials. */
public interface AuthPasskeyGateway {

    /** Lists all passkey credentials associated with an account. */
    /** @param userId account identifier */
    /** @return credentials owned by the account */
    List<PasskeyCredential> findByUserId(Long userId);

    /** Deletes the supplied persisted passkey rows. */
    /** @param credentials credential entities selected for deletion */
    void deleteAll(List<PasskeyCredential> credentials);

    /** Persists one credential and returns its stored representation. */
    /** @param credential credential with owner and public authenticator data */
    /** @return persisted credential */
    PasskeyCredential save(PasskeyCredential credential);
}
