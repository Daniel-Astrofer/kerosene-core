package source.transactions.dto;

import java.math.BigDecimal;

public record LightningPaymentRequestDTO(
        String fromWalletName,
        String paymentRequest,
        BigDecimal amount,
        BigDecimal maxRoutingFeeBtc,
        String description,
        String totpCode,
        String passkeyAssertionResponseJSON,
        String confirmationPassphrase) {
}
