package source.content.dto;

/**
 * Design-system token references only (no freeform hex in v1).
 */
public record HomeStyleTokensDTO(
        String colorToken,
        String fontWeight) {
}
