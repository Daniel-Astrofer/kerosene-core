package com.kerosene.auth.application.port.out;

import com.kerosene.auth.model.entity.UserDataBase;

/** Outbound persistence port for user lookup, uniqueness checks, and storage. */
public interface AuthUserGateway {

    /** Finds an account by the username value supplied by the application. */
    /** @param username normalized username */
    /** @return persisted account or {@code null} when absent */
    UserDataBase findByUsername(String username);

    /** Checks whether a username is already assigned without loading the full account entity. */
    /** @param username normalized username */
    /** @return true when an account uses the username */
    boolean existsByUsername(String username);

    /** Persists an account entity and returns the stored representation. */
    /** @param user account to persist */
    /** @return persisted account, including generated values */
    UserDataBase save(UserDataBase user);
}
