package com.kerosene.common.financial;

/**
 * Internal KFE-to-Core user lookup request.
 *
 * <p>Exactly one lookup key must be provided.</p>
 */
public record FinancialUserDirectoryLookupRequest(String username, Long userId) {

    public static FinancialUserDirectoryLookupRequest byUsername(String username) {
        return new FinancialUserDirectoryLookupRequest(username, null);
    }

    public static FinancialUserDirectoryLookupRequest byUserId(Long userId) {
        return new FinancialUserDirectoryLookupRequest(null, userId);
    }
}
