package com.kerosene.content.dto;

/**
 * Design-system token references only; schema v1 deliberately excludes arbitrary color values.
 * @param colorToken named design-system color token resolved by the client theme
 * @param fontWeight named typography weight supported by the client design system
 */
public record HomeStyleTokensDTO(
        String colorToken,
        String fontWeight) {
}
