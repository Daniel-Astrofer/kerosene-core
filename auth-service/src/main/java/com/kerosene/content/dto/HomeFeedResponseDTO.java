package com.kerosene.content.dto;

import java.util.List;

/**
 * Versioned feed response with cache duration, presentation preferences, and eligible cards.
 * @param version feed snapshot identifier used to detect updates
 * @param ttlSeconds client cache lifetime for this response
 * @param balanceView balance presentation mode paired with the feed
 * @param locale locale used for card text and number formatting
 * @param timeZone timezone used by time-dependent feed content
 * @param items ordered home feed cards
 */
public record HomeFeedResponseDTO(
        String version,
        int ttlSeconds,
        String balanceView,
        String locale,
        String timeZone,
        List<HomeFeedItemDTO> items) {
}
