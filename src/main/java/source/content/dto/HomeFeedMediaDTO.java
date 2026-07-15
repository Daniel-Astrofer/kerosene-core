package source.content.dto;

/**
 * Media payload for a home feed card.
 * type: ICON | IMAGE | LOTTIE | VIDEO
 */
public record HomeFeedMediaDTO(
        String type,
        String iconKey,
        String url,
        String posterUrl,
        Double aspectRatio) {
}
