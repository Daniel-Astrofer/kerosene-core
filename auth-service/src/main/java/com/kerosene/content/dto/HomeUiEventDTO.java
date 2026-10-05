package com.kerosene.content.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Realtime home UI event over STOMP /user/queue/home-ui.
 * type: HOME_UI_SNAPSHOT | HOME_UI_PATCH | HOME_UI_GREETING | HOME_UI_FEED_DELTA
 * @param type event kind that determines how the client applies the payload
 * @param version composition version used for ordering and stale-event rejection
 * @param payload event body whose shape depends on the event type
 */
public record HomeUiEventDTO(
        String type,
        String version,
        JsonNode payload) {
}
