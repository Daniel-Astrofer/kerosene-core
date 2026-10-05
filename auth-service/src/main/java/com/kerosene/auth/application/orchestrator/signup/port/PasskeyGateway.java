package com.kerosene.auth.application.orchestrator.signup.port;

import java.util.List;

import com.kerosene.auth.model.entity.PasskeyCredential;

/** Persistence port for passkey credentials used during signup finalization. */
public interface PasskeyGateway {

    /** Persists one passkey credential and returns the stored entity. */
    /** @param credential credential and owner data to persist */
    /** @return persisted credential */
    PasskeyCredential save(PasskeyCredential credential);

    /** Lists passkey credentials belonging to the supplied account. */
    /** @param userId account identifier */
    /** @return credentials owned by the account, possibly empty */
    List<PasskeyCredential> findByUserId(Long userId);
}
