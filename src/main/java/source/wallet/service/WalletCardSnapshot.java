package source.wallet.service;

import java.time.LocalDateTime;

public record WalletCardSnapshot(
        String holderName,
        String maskedNumber,
        String suffix,
        int sequence,
        String rotationStatus,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime nextRotationAt,
        LocalDateTime lastRotatedAt,
        String previousSuffix,
        LocalDateTime previousExpiresAt) {
}
