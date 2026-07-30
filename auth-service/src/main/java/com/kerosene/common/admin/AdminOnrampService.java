package com.kerosene.common.admin;

import java.time.Instant;

/**
 * Service interface for onramp admin operations.
 */
public interface AdminOnrampService {

    OnrampOrderDetail findOrder(String id);

    record OnrampOrderDetail(
            String id,
            String userId,
            String fiatCurrency,
            String fiatAmount,
            String btcAmount,
            String paymentMethod,
            String status,
            String provider,
            Instant createdAt,
            Instant completedAt) {}
}
