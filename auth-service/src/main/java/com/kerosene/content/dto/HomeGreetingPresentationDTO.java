package com.kerosene.content.dto;

/**
 * How the client plays greeting messages.
 *
 * <p>playPolicy: ONCE | LOOP
 * <ul>
 *   <li>ONCE — show market lines once (marquee), then restore time-of-day + header actions
 *   <li>LOOP — keep rotating (legacy ticker)
 * </ul>
 * @param playPolicy whether to play once and restore or continue looping
 * @param hideActionsWhilePlaying whether header actions are hidden during playback
 * @param restoreActionsAfterPlay whether hidden actions return after one-time playback
 * @param pushDownBalanceWhilePlaying whether the balance shifts below the greeting
 * @param pushDownBalancePx vertical balance offset in logical pixels
 * @param compressLayoutWhilePlaying whether the header reduces spacing during playback
 */
public record HomeGreetingPresentationDTO(
        String playPolicy,
        Boolean hideActionsWhilePlaying,
        Boolean restoreActionsAfterPlay,
        Boolean pushDownBalanceWhilePlaying,
        Integer pushDownBalancePx,
        Boolean compressLayoutWhilePlaying) {
}
