package source.content.dto;

/**
 * One composable card in the home education / promo feed.
 * kind: EDUCATION | ANNOUNCEMENT | PROMO | FEATURE
 */
public record HomeFeedItemDTO(
        String id,
        String kind,
        int priority,
        String layout,
        String title,
        String body,
        String tag,
        HomeFeedMediaDTO media,
        HomeFeedCtaDTO cta,
        String surfaceTint,
        String accent,
        String campaignId) {
}
