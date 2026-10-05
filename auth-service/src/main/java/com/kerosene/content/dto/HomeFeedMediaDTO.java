package com.kerosene.content.dto;

/**
 * Media payload for a home feed card.
 * type: ICON | IMAGE | LOTTIE | VIDEO
 * @param type media kind that selects the client renderer
 * @param iconKey symbolic icon name for ICON media
 * @param url source URL for remote image, animation, or video
 * @param posterUrl preview image URL used before video playback
 * @param aspectRatio preferred media width-to-height ratio
 */
public record HomeFeedMediaDTO(
        String type,
        String iconKey,
        String url,
        String posterUrl,
        Double aspectRatio) {
}
