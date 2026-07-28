package com.kerosene.common.financial;

import java.util.UUID;

public record FinancialDepositConfirmedNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID walletId,
        String rail,
        long creditedSats,
        int confirmations) {

    public FinancialDepositConfirmedNotificationRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (transactionId == null) throw new IllegalArgumentException("transactionId required");
        if (walletId == null) throw new IllegalArgumentException("walletId required");
        if (rail == null || rail.isBlank()) throw new IllegalArgumentException("rail required");
        if (creditedSats <= 0) throw new IllegalArgumentException("creditedSats must be > 0, got: " + creditedSats);
        if (confirmations < 0) throw new IllegalArgumentException("confirmations must be >= 0, got: " + confirmations);
    }
}
