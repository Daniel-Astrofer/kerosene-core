package source.content.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Realtime home UI event over STOMP /user/queue/home-ui.
 * type: HOME_UI_SNAPSHOT | HOME_UI_PATCH | HOME_UI_GREETING | HOME_UI_FEED_DELTA
 */
public record HomeUiEventDTO(
        String type,
        String version,
        JsonNode payload) {
}
