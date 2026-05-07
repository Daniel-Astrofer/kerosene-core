package source.payments.dto;

import source.payments.model.PaymentEnums;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentQuoteResponse(
        UUID paymentIntentId,
        Instant quoteExpiresAt,
        PaymentEnums.PaymentRail rail,
        PaymentEnums.FeeMode feeMode,
        String receiverDisplayName,
        String receiverAmountFiat,
        long receiverAmountSats,
        String totalDebitFiat,
        long totalDebitSats,
        String networkFeeFiat,
        long networkFeeSats,
        String keroseneFeeFiat,
        long keroseneFeeSats,
        List<String> warnings,
        boolean requiresConfirmation) {
}
