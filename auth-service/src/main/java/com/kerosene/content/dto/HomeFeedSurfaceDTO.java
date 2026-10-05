package com.kerosene.content.dto;

import java.util.List;

/**
 * heightToken: compact | regular | expanded
 * defaultAnimation: NONE | FADE | SLIDE | PULSE
 * @param heightToken named vertical size preset
 * @param heightPx explicit height override in logical pixels
 * @param cardPadding internal card inset in logical pixels
 * @param gap spacing between neighboring cards in logical pixels
 * @param defaultAnimation animation applied when a card enters or updates
 * @param items ordered cards rendered in the surface
 */
public record HomeFeedSurfaceDTO(
        String heightToken,
        Integer heightPx,
        Integer cardPadding,
        Integer gap,
        String defaultAnimation,
        List<HomeFeedItemDTO> items) {
}
