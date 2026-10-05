package com.kerosene.content.dto;

import java.util.List;

/**
 * mode: STATIC | OVERRIDE | TICKER | EPHEMERAL
 * EPHEMERAL = play messages once (usually marquee), then fall back to TIME_OF_DAY.
 * @param mode greeting playback mode
 * @param fallback greeting template restored after temporary content ends
 * @param messages lines eligible for override, ticker, or ephemeral playback
 * @param rotation interval and looping policy for rotating messages
 * @param style design-system style tokens applied to greeting text
 * @param presentation action and balance behavior while messages play
 */
public record HomeGreetingDTO(
        String mode,
        HomeGreetingFallbackDTO fallback,
        List<HomeGreetingMessageDTO> messages,
        HomeGreetingRotationDTO rotation,
        HomeStyleTokensDTO style,
        HomeGreetingPresentationDTO presentation) {
}
