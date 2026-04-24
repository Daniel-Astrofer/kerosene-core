package source.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for wallet response that avoids circular references and exposes only
 * necessary data.
 */
public record WalletResponseDTO(
                Long id,
                String name,
                String passphraseHash,
                LocalDateTime createdAt,
                LocalDateTime updatedAt,
                Boolean isActive,
                String totpUri,
                String depositAddress,
                String lightningAddress,
                String walletMode,
                Boolean xpubConfigured,
                String cardType,
                String cardHolderName,
                String cardMaskedNumber,
                String cardNumberSuffix,
                Integer cardSequence,
                String cardRotationStatus,
                LocalDateTime cardIssuedAt,
                LocalDateTime cardExpiresAt,
                LocalDateTime cardNextRotationAt,
                LocalDateTime cardLastRotatedAt,
                String previousCardNumberSuffix,
                LocalDateTime previousCardExpiresAt,
                BigDecimal withdrawalFeeRate,
                BigDecimal depositFeeRate) {
}
