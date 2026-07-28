package com.kerosene.common.financial;

/**
 * Internal KFE-to-Core user lookup request.
 *
 * <p>Exactly one lookup key must be provided.</p>
 */
public record FinancialUserDirectoryLookupRequest(String username, Long userId) {

    public FinancialUserDirectoryLookupRequest {
        if (username == null && userId == null) {
            throw new IllegalArgumentException("exactly one of username or userId required");
        }
        // username is nullable when searching by userId
    }

    public static FinancialUserDirectoryLookupRequest byUsername(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username required");
        return new FinancialUserDirectoryLookupRequest(username, null);
    }

    public static FinancialUserDirectoryLookupRequest byUserId(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        return new FinancialUserDirectoryLookupRequest(null, userId);
    }
}
