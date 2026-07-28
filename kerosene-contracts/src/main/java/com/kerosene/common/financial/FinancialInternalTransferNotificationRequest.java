package com.kerosene.common.financial;

import java.util.UUID;

public record FinancialInternalTransferNotificationRequest(
        Long userId,
        UUID transactionId,
        UUID walletId,
        long amountSats) {

    public FinancialInternalTransferNotificationRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (transactionId == null) throw new IllegalArgumentException("transactionId required");
        if (walletId == null) throw new IllegalArgumentException("walletId required");
        if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0, got: " + amountSats);
    }
}
