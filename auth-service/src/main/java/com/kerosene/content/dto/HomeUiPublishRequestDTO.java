package com.kerosene.content.dto;

/**
 * Internal publish command for a home UI override or live event.
 *
 * <p>action: UPSERT_OVERRIDE | PUSH_SNAPSHOT | PUSH_PATCH | PUSH_GREETING
 * scope: GLOBAL | USER | SEGMENT (for UPSERT_OVERRIDE)
 * @param action operation to apply: upsert an override or push a snapshot, patch, or greeting
 * @param userId target account for USER scope or user-specific pushes; null for broader scope
 * @param scope audience scope for a persistent override
 * @param segmentKey segment selector used only with SEGMENT scope
 * @param priority ordering weight when multiple overrides apply
 * @param active whether the override should be eligible for selection
 * @param startsAt inclusive start timestamp for a scheduled override
 * @param endsAt exclusive/end timestamp after which the override is not eligible
 * @param payloadJson serialized composition payload consumed by the home UI schema
 * @param balanceView optional balance presentation mode included in the published composition
 * @param locale optional locale override for the published composition
 * @param timeZone optional timezone override for the published composition
 */
public record HomeUiPublishRequestDTO(
        String action,
        Long userId,
        String scope,
        String segmentKey,
        Integer priority,
        Boolean active,
        String startsAt,
        String endsAt,
        String payloadJson,
        String balanceView,
        String locale,
        String timeZone) {
}
