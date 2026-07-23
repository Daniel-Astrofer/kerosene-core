package com.kerosene.auth.dto;

import java.time.LocalDateTime;

public record AdminKeyStatusDTO(
        boolean configured,
        String status,
        String fingerprint,
        LocalDateTime createdAt,
        LocalDateTime revokedAt) {
}
