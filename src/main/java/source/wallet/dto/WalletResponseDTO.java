package source.wallet.dto;

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
                String totpUri) {
}
