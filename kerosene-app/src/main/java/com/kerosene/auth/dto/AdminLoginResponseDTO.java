package source.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminLoginResponseDTO(
        String status,
        boolean requiresMobileApproval,
        UUID attemptId,
        LocalDateTime expiresAt,
        String token,
        String message) {
}
