package source.content.dto;

public record HomeHeaderActionsDTO(
        HomeActionVisibilityDTO balanceVisibility,
        HomeActionVisibilityDTO notifications,
        HomeActionVisibilityDTO settings) {
}
