package source.content.dto;

import java.util.List;

/**
 * mode: STATIC | OVERRIDE | TICKER | EPHEMERAL
 * EPHEMERAL = play messages once (usually marquee), then fall back to TIME_OF_DAY.
 */
public record HomeGreetingDTO(
        String mode,
        HomeGreetingFallbackDTO fallback,
        List<HomeGreetingMessageDTO> messages,
        HomeGreetingRotationDTO rotation,
        HomeStyleTokensDTO style,
        HomeGreetingPresentationDTO presentation) {
}
