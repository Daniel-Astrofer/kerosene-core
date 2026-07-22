package source.content.dto;

/**
 * Logical spacing units (logical pixels at density 1). Client clamps.
 */
public record HomeLayoutDTO(
        Integer sectionGapAfterHeader,
        Integer sectionGapAfterBalance,
        Integer sectionGapBeforeFeed,
        Integer horizontalPadding) {
}
