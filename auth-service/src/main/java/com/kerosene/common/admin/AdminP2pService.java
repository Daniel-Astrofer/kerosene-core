package com.kerosene.common.admin;

import java.time.Instant;

/**
 * Service interface for P2P admin operations.
 */
public interface AdminP2pService {

    P2pOrderDetail findOrder(String id);

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
