package source.transactions.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LightningInvoiceRequestDTO(
        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 96, message = "idempotencyKey must have at most 96 characters")
        String idempotencyKey,
        @NotBlank(message = "walletName is required")
        String walletName,
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00000001", message = "amount must be greater than zero")
        @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount")
        @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")
        BigDecimal amount,
        String memo,
        Integer expiresInSeconds) {
}
