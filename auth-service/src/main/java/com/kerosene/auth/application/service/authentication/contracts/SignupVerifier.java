package com.kerosene.auth.application.service.authentication.contracts;

/** Port exposing the ordered checks required before a signup session can be created. */
public interface SignupVerifier {

    /** Checks that username is not null or blank. */
    /** @param username candidate username */
    void checkUsernameNotNull(String username);

    /** Checks that passphrase characters are present. */
    /** @param passphrase candidate secret characters */
    void checkPassphraseNotNull(char[] passphrase);

    /** Checks username character policy. */
    /** @param username candidate username */
    void checkUsernameFormat(String username);

    /** Checks maximum username length. */
    /** @param username candidate username */
    void checkUsernameLength(String username);

    /** Checks maximum passphrase length. */
    /** @param passphrase candidate secret characters */
    void checkPassphraseLength(char[] passphrase);

    /** Checks minimum passphrase length and required character categories. */
    /** @param passphrase candidate secret characters */
    void checkPassphraseBip39(char[] passphrase);

    /** Checks that the username is not already stored. */
    /** @param username candidate username */
    void checkUsernameExists(String username);

    /** Executes the complete signup credential validation pipeline. */
    /** @param username candidate username */
    /** @param passphrase candidate secret characters */
    /** @return true when every validation step succeeds */
    boolean verify(String username, char[] passphrase);

}
