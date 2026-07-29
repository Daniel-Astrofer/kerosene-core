package com.kerosene.content.dto;

/**
 * Optional call-to-action on a home feed card.
 * action: NAVIGATE | EXTERNAL | NONE
 */
public record HomeFeedCtaDTO(
        String label,
        String action,
        String target) {
}
