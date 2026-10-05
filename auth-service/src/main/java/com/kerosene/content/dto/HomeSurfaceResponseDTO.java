package com.kerosene.content.dto;

/**
 * Full home surface composition envelope returned to mobile and web clients.
 * Schema version 2 adds Communication Stage theater content and a resting header.
 * @param schemaVersion contract version used to select compatible client decoding
 * @param version version identifier for the selected composition snapshot
 * @param ttlSeconds cache lifetime advertised for this surface
 * @param balanceView balance presentation mode selected for the current account
 * @param locale locale used to resolve localized text and formatting
 * @param timeZone timezone used for time-dependent surface content
 * @param layout global layout tokens and surface geometry
 * @param header primary header content
 * @param feed list or stream of home feed cards
 * @param stage active communication stage or neutral idle stage
 * @param restingHeader header state displayed when no temporary stage is active
 */
public record HomeSurfaceResponseDTO(
        int schemaVersion,
        String version,
        int ttlSeconds,
        String balanceView,
        String locale,
        String timeZone,
        HomeLayoutDTO layout,
        HomeHeaderDTO header,
        HomeFeedSurfaceDTO feed,
        HomeStageDTO stage,
        HomeRestingHeaderDTO restingHeader) {
}
