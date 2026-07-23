package com.kerosene.content.dto;

import java.util.List;

/**
 * heightToken: compact | regular | expanded
 * defaultAnimation: NONE | FADE | SLIDE | PULSE
 */
public record HomeFeedSurfaceDTO(
        String heightToken,
        Integer heightPx,
        Integer cardPadding,
        Integer gap,
        String defaultAnimation,
        List<HomeFeedItemDTO> items) {
}
