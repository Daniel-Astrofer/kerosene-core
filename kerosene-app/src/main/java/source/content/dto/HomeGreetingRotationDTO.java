package source.content.dto;

/**
 * intervalMs: dwell time per message (and marquee window).
 * loop: false → play queue once then stop (preferred for market insights).
 */
public record HomeGreetingRotationDTO(
        Integer intervalMs,
        Boolean loop) {
}
