package com.kerosene.content.dto;

/**
 * Visibility state for one optional home-header action.
 * @param visible true to show the action, false to hide it, null when client policy supplies the default
 */
public record HomeActionVisibilityDTO(Boolean visible) {
}
