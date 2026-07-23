package com.kerosene.content.dto;

/**
 * Full home surface composition envelope.
 * schemaVersion 2 adds Communication Stage (theater) + restingHeader.
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
