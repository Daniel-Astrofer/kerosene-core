package com.kerosene.content.dto;

/**
 * Logical spacing units (logical pixels at density 1). Client clamps.
 * @param sectionGapAfterHeader spacing from header to the following section
 * @param sectionGapAfterBalance spacing below the balance panel
 * @param sectionGapBeforeFeed spacing above the feed surface
 * @param horizontalPadding shared left and right content inset
 */
public record HomeLayoutDTO(
        Integer sectionGapAfterHeader,
        Integer sectionGapAfterBalance,
        Integer sectionGapBeforeFeed,
        Integer horizontalPadding) {
}
