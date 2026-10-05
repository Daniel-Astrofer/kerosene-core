package com.kerosene.content.dto;

/**
 * template: TIME_OF_DAY
 * @param template fallback greeting template identifier
 * @param includeName whether the client may personalize the template with the user's name
 */
public record HomeGreetingFallbackDTO(
        String template,
        Boolean includeName) {
}
