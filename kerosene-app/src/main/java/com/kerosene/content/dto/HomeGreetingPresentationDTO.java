package com.kerosene.content.dto;

/**
 * How the client plays greeting messages.
 *
 * <p>playPolicy: ONCE | LOOP
 * <ul>
 *   <li>ONCE — show market lines once (marquee), then restore time-of-day + header actions
 *   <li>LOOP — keep rotating (legacy ticker)
 * </ul>
 */
public record HomeGreetingPresentationDTO(
        String playPolicy,
        Boolean hideActionsWhilePlaying,
        Boolean restoreActionsAfterPlay,
        Boolean pushDownBalanceWhilePlaying,
        Integer pushDownBalancePx,
        Boolean compressLayoutWhilePlaying) {
}
