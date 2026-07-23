package com.kerosene.content.dto;

/**
 * One ticker / override greeting line.
 * Placeholders: {name}
 * animation: NONE | FADE | SLIDE
 */
public record HomeGreetingMessageDTO(
        String id,
        String text,
        Integer durationMs,
        Integer priority,
        String animation,
        HomeStyleTokensDTO style,
        String expiresAt) {
}
