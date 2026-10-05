package com.kerosene.auth.application.service.crypto.contracts;

/** Boundary for one-way hashing of sensitive character input and subsequent verification. */
public interface Hasher {

    /** Hashes input according to the implementation's configured algorithm and secret material. */
    /** @param input sensitive characters; implementations may clear the supplied array */
    /** @return encoded hash including any algorithm parameters and salt */
    String hash(char[] input);

    /** Verifies input against an encoded hash using the same algorithm and secret material. */
    /** @param input candidate sensitive characters */
    /** @param hash previously encoded hash */
    /** @return whether the candidate matches */
    Boolean verify(char[] input, String hash);
}
