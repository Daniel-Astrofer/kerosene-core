package source.transactions.dto;

import java.math.BigDecimal;

public record OnchainSendRequestDTO(
        String fromWalletName,
        String toAddress,
        BigDecimal amount,
        String description,
        String totpCode,
        String passkeyAssertionResponseJSON,
        String confirmationPassphrase) {
}
