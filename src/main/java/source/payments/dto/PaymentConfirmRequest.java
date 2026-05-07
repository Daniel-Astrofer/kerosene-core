package source.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentConfirmRequest(
        @NotBlank @Size(max = 128) String idempotencyKey,
        @Size(max = 512) String userConfirmationToken,
        @NotNull Long acceptedTotalDebitSats,
        @NotNull Long acceptedReceiverAmountSats) {
}
