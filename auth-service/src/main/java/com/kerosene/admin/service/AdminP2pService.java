package com.kerosene.admin.service;

import java.time.Instant;

/** Read-only contract for administrative inspection of a peer-to-peer order. */
public interface AdminP2pService {

    /**
     * Finds one P2P order by its platform identifier.
     *
     * @param id identifier supplied by the administrative route
     * @return order participants, market terms, payment method, status, and timestamps
     */
    P2pOrderDetail findOrder(String id);

    /**
     * Administrative P2P order projection with precision-preserving decimal strings.
     *
     * @param id order identifier
     * @param makerId participant who created the offer
     * @param takerId participant who accepted or matched the offer
     * @param asset traded asset code
     * @param amount traded amount as a decimal string
     * @param price quoted price as a decimal string
     * @param status current P2P lifecycle state
     * @param paymentMethod settlement/payment method label
     * @param createdAt order creation instant
     * @param completedAt completion instant, null while unfinished or unavailable
     */
    record P2pOrderDetail(
            String id,
            String makerId,
            String takerId,
            String asset,
            String amount,
            String price,
            String status,
            String paymentMethod,
            Instant createdAt,
            Instant completedAt) {}
}
