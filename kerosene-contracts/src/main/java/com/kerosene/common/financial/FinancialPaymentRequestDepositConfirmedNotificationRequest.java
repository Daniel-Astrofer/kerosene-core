package com.kerosene.common.financial;

import java.util.UUID;

public record FinancialPaymentRequestDepositConfirmedNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID paymentRequestId,
        String publicId,
        UUID walletId,
        String rail,
        long creditedSats) {

    public FinancialPaymentRequestDepositConfirmedNotificationRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (transactionId == null) throw new IllegalArgumentException("transactionId required");
        if (paymentRequestId == null) throw new IllegalArgumentException("paymentRequestId required");
        if (publicId == null || publicId.isBlank()) throw new IllegalArgumentException("publicId required");
        if (walletId == null) throw new IllegalArgumentException("walletId required");
        if (rail == null || rail.isBlank()) throw new IllegalArgumentException("rail required");
        if (creditedSats <= 0) throw new IllegalArgumentException("creditedSats must be > 0, got: " + creditedSats);
    }
}
