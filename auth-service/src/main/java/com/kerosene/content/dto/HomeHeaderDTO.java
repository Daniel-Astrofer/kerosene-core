package com.kerosene.content.dto;

/**
 * actions = resting state after ephemeral play (usually all visible).
 * While messages play, client applies presentation.hideActionsWhilePlaying etc.
 */
public record HomeHeaderDTO(
        HomeGreetingDTO greeting,
        HomeHeaderActionsDTO actions,
        HomeHeaderSpacingDTO spacing) {
}
