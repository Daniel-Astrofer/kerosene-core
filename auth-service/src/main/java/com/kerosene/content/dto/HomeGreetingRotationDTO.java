package com.kerosene.content.dto;

/**
 * intervalMs: dwell time per message (and marquee window).
 * loop: false → play queue once then stop (preferred for market insights).
 * @param intervalMs time allotted to each line and marquee window in milliseconds
 * @param loop whether the message queue restarts after its final line
 */
public record HomeGreetingRotationDTO(
        Integer intervalMs,
        Boolean loop) {
}
