package com.kerosene.content.dto;

/**
 * Logical spacing values for the action group and the area following a header greeting.
 * @param betweenActions spacing between adjacent header controls in logical pixels
 * @param afterGreeting spacing after greeting text before the action group
 */
public record HomeHeaderSpacingDTO(
        Integer betweenActions,
        Integer afterGreeting) {
}
