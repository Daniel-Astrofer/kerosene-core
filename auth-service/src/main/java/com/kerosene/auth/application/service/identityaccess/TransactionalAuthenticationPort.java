package com.kerosene.auth.application.service.identityaccess;

/** Application port that authorizes high-risk operations against account security factors. */
public interface TransactionalAuthenticationPort {

    /** Authorizes the requested operation scope or throws a factor/policy validation error. */
    /** @param request operation scope, owner identities, and presented factors */
    /** @return resolved user and optional platform transaction signature */
    TransactionalAuthenticationResult authorize(TransactionalAuthenticationRequest request);
}
