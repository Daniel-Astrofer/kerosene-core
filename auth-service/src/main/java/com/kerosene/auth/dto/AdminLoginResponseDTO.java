package com.kerosene.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Result of an administrator login attempt, including pending approval or an issued credential.
 * @param status outcome such as authenticated or awaiting mobile approval
 * @param requiresMobileApproval whether a trusted mobile device must approve the attempt
 * @param attemptId identifier used to poll or decide a pending access attempt
 * @param expiresAt expiration time for the pending attempt
 * @param token administrator token when authentication completed immediately
 * @param message safe status text intended for the administrator client
 */
public record AdminLoginResponseDTO(
        String status,
        boolean requiresMobileApproval,
        UUID attemptId,
        LocalDateTime expiresAt,
        String token,
        String message) {
}
