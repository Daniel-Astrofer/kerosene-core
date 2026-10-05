package com.kerosene.admin.service;

import java.time.Instant;

/** Read-only contract for administrative inspection of a fiat-to-Bitcoin onramp order. */
public interface AdminOnrampService {

    /**
     * Finds a single onramp order by its external or platform identifier.
     *
     * @param id order identifier supplied by the administrative route
     * @return financial/provider metadata and lifecycle timestamps for the order
     */
    OnrampOrderDetail findOrder(String id);

    /**
     * Administrative onramp order projection; fiat and Bitcoin amounts remain strings for decimal precision.
     *
     * @param id order identifier
     * @param userId identifier of the customer associated with the order
     * @param fiatCurrency ISO or provider fiat currency code
     * @param fiatAmount fiat amount represented without binary floating-point conversion
     * @param btcAmount Bitcoin amount represented without binary floating-point conversion
     * @param paymentMethod provider payment rail or method label
     * @param status current provider/platform lifecycle state
     * @param provider onramp integration/provider name
     * @param createdAt order creation instant
     * @param completedAt completion instant, null while unfinished or unavailable
     */
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
