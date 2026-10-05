package com.kerosene.auth.dto;

import java.time.LocalDateTime;

/**
 * Public summary of administrator-key enrollment, lifecycle state, and audit timestamps.
 * @param configured whether an administrator key is currently associated with the account
 * @param status key lifecycle status exposed to the client
 * @param fingerprint non-secret fingerprint used to identify the enrolled key
 * @param createdAt enrollment timestamp
 * @param revokedAt revocation timestamp, or null while the key remains active
 */
public record AdminKeyStatusDTO(
        boolean configured,
        String status,
        String fingerprint,
        LocalDateTime createdAt,
        LocalDateTime revokedAt) {
}
