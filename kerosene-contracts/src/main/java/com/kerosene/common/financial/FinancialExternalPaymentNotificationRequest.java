package com.kerosene.common.financial;

import java.util.UUID;

public record FinancialExternalPaymentNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID walletId,
        String rail,
        long amountSats) {

    public FinancialExternalPaymentNotificationRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (transactionId == null) throw new IllegalArgumentException("transactionId required");
        if (walletId == null) throw new IllegalArgumentException("walletId required");
        if (rail == null || rail.isBlank()) throw new IllegalArgumentException("rail required");
        if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0, got: " + amountSats);
    }
}
