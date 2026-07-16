package source.content.dto;

/**
 * Internal publish request for home UI composition overrides / live push.
 *
 * <p>action: UPSERT_OVERRIDE | PUSH_SNAPSHOT | PUSH_PATCH | PUSH_GREETING
 * scope: GLOBAL | USER | SEGMENT (for UPSERT_OVERRIDE)
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
