package com.kerosene.content.dto;

/**
 * Visibility policies for balance, notifications, and settings actions in the home header.
 * @param balanceVisibility policy for the balance control
 * @param notifications policy for the notifications control
 * @param settings policy for the settings control
 */
public record HomeHeaderActionsDTO(
        HomeActionVisibilityDTO balanceVisibility,
        HomeActionVisibilityDTO notifications,
        HomeActionVisibilityDTO settings) {
}
