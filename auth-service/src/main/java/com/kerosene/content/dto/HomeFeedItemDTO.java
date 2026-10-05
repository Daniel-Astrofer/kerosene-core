package com.kerosene.content.dto;

/**
 * One composable card in the home education / promo feed.
 * kind: EDUCATION | ANNOUNCEMENT | PROMO | FEATURE
 * @param id stable identifier used to key and acknowledge this feed card
 * @param kind card category that controls client treatment
 * @param priority ordering weight when multiple cards are eligible
 * @param layout named client layout preset for the card
 * @param title localized card heading
 * @param body card body or templated explanatory text
 * @param tag short category label shown alongside the card
 * @param media optional image, icon, animation, or video payload
 * @param cta optional action offered to the user
 * @param surfaceTint semantic surface token used behind the card
 * @param accent semantic accent token used to highlight the card
 * @param campaignId campaign identifier used for grouping and measurement
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
