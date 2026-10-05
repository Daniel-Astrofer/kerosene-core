package com.kerosene.auth.application.service.authentication.login;

import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.entity.UserDataBase;

/** Mutable data carried through ordered login handlers, including secret buffers that must be wiped. */
public class LoginValidationContext {

    /** Original request DTO, including the caller-owned passphrase array. */
    private final UserDTOContract dto;
    /** Canonical username produced by required-field validation. */
    private String normalizedUsername;
    /** Redis throttle key created by the rate-limit handler. */
    private String rateLimitKey;
    /** Account entity loaded for password verification. */
    private UserDataBase user;
    /** Whitespace-normalized passphrase buffer used for hash verification. */
    private char[] normalizedPassphrase;

    /** Creates a validation context for one request. */
    /** @param dto original login request */
    public LoginValidationContext(UserDTOContract dto) {
        this.dto = dto;
    }

    /** Returns the original request. */
    /** @return submitted login DTO */
    public UserDTOContract getDto() {
        return dto;
    }

    /** Returns the normalized username set by the first handler. */
    /** @return canonical username */
    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    /** Stores the canonical username for later handlers. */
    /** @param normalizedUsername canonical username */
    public void setNormalizedUsername(String normalizedUsername) {
        this.normalizedUsername = normalizedUsername;
    }

    /** Returns the key used for the current request's login attempt count. */
    /** @return rate-limit key, or null before the throttle handler runs */
    public String getRateLimitKey() {
        return rateLimitKey;
    }

    /** Stores the rate-limit key created by the throttle handler. */
    /** @param rateLimitKey Redis throttle key */
    public void setRateLimitKey(String rateLimitKey) {
        this.rateLimitKey = rateLimitKey;
    }

    /** Returns the account loaded for passphrase verification. */
    /** @return loaded account, or null before the lookup handler runs */
    public UserDataBase getUser() {
        return user;
    }

    /** Stores the account loaded for later chain handlers. */
    /** @param user persisted account */
    public void setUser(UserDataBase user) {
        this.user = user;
    }

    /** Returns the normalized secret buffer that cleanup must zero. */
    /** @return normalized passphrase characters */
    public char[] getNormalizedPassphrase() {
        return normalizedPassphrase;
    }

    /** Stores the normalized passphrase buffer for verification and finally-block cleanup. */
    /** @param normalizedPassphrase normalized mutable characters */
    public void setNormalizedPassphrase(char[] normalizedPassphrase) {
        this.normalizedPassphrase = normalizedPassphrase;
    }
}
