package com.kerosene.content.dto;

/**
 * Optional call-to-action on a home feed card.
 * action: NAVIGATE | EXTERNAL | NONE
 * @param label user-visible action text
 * @param action client action kind
 * @param target route or external destination interpreted according to action
 */
public record HomeFeedCtaDTO(
        String label,
        String action,
        String target) {
}
