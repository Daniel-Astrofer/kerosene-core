package source.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import source.payments.model.PaymentEnums;

public record PaymentQuoteRequest(
        @NotNull PaymentEnums.PaymentRail rail,
        @NotNull PaymentEnums.FeeMode feeMode,
        @NotBlank @Size(max = 40) String amountFiat,
        @NotBlank @Size(max = 8) String fiatCurrency,
        @NotBlank @Size(max = 16) String asset,
        @Size(max = 255) String receiverIdentifier,
        @Size(max = 2048) String externalDestination,
        PaymentEnums.OnchainSpeed speed) {
}
