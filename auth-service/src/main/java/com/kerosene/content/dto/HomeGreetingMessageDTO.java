package com.kerosene.content.dto;

/**
 * One ticker / override greeting line.
 * Placeholders: {name}
 * animation: NONE | FADE | SLIDE
 * @param id stable identifier for the greeting line
 * @param text localized text template with optional supported placeholders
 * @param durationMs time the line remains active before rotation
 * @param priority ordering weight used when choosing greeting content
 * @param animation client transition used when displaying this line
 * @param style design-system typography and color tokens
 * @param expiresAt optional expiration timestamp after which the line is ignored
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
