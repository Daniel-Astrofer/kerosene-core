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
                Boolean xpubConfigured,
                String cardType,
                BigDecimal withdrawalFeeRate,
                BigDecimal depositFeeRate) {
}
