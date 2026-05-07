package source.payments.dto;

import source.payments.model.PaymentEnums;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID paymentIntentId,
        PaymentEnums.PaymentIntentStatus status,
        PaymentEnums.PaymentRail rail,
        PaymentEnums.FeeMode feeMode,
        String receiverDisplayName,
        long receiverAmountSats,
        long totalDebitSats,
        long networkFeeSats,
        long keroseneFeeSats,
        Instant quoteExpiresAt,
        String failureCode,
        String failureMessage,
        List<String> warnings) {
}
