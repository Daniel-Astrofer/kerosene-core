package com.kerosene.content.dto;

/**
 * actions = resting state after ephemeral play (usually all visible).
 * While messages play, client applies presentation.hideActionsWhilePlaying etc.
 * @param greeting greeting mode, messages, rotation, and playback rules
 * @param actions per-action visibility policy restored in the resting state
 * @param spacing logical spacing between actions and after the greeting
 */
public record HomeHeaderDTO(
        HomeGreetingDTO greeting,
        HomeHeaderActionsDTO actions,
        HomeHeaderSpacingDTO spacing) {
}
