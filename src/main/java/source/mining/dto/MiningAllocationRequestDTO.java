package source.mining.dto;

import java.math.BigDecimal;

public record MiningAllocationRequestDTO(
        String walletName,
        Long rigId,
        BigDecimal requestedHashrate,
        BigDecimal budgetBtc,
        Integer durationHours,
        String payoutAddress,
        String poolUrl,
        String workerName,
        String totpCode,
        String passkeyAssertionResponseJSON,
        String confirmationPassphrase) {
}
