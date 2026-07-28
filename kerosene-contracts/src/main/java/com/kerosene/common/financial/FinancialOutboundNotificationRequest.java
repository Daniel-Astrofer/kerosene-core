package com.kerosene.common.financial;

import java.util.UUID;

public record FinancialOutboundNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID walletId,
        String rail,
        long amountSats,
        int confirmations,
        String destinationHint) {

    public FinancialOutboundNotificationRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (transactionId == null) throw new IllegalArgumentException("transactionId required");
        if (walletId == null) throw new IllegalArgumentException("walletId required");
        if (rail == null || rail.isBlank()) throw new IllegalArgumentException("rail required");
        if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0, got: " + amountSats);
        if (confirmations < 0) throw new IllegalArgumentException("confirmations must be >= 0, got: " + confirmations);
    }
}
