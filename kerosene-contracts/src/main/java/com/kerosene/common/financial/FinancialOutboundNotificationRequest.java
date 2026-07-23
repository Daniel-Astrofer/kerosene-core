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
}
