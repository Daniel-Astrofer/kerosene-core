package source.auth.dto;

import java.time.LocalDateTime;

public record AppPinStatusDTO(
        boolean enabled,
        boolean configured,
        boolean locked,
        int failedAttempts,
        int remainingAttempts,
        int maxAttempts,
        int minPinLength,
        int maxPinLength,
        boolean resettableWithTotp,
        boolean deviceScoped,
        LocalDateTime lockedUntil,
        LocalDateTime lastVerifiedAt,
        LocalDateTime updatedAt) {
}
