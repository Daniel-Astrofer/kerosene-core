package com.kerosene.content.dto;

/**
 * Header content and controls restored after any temporary greeting or communication stage ends.
 * @param greeting fallback greeting template and personalization rule
 * @param actions action placement and visibility restored with the greeting
 */
public record HomeRestingHeaderDTO(Greeting greeting, Actions actions) {

    /**
     * Greeting template used in the resting header.
     * @param template template identifier, commonly TIME_OF_DAY
     * @param includeName whether the client may insert the authenticated user's display name
     */
    public record Greeting(String template, Boolean includeName) {}

    /**
     * Visibility and placement of actions restored with the resting header.
     * @param placement location of the action group relative to the greeting
     * @param balanceVisibility whether the balance action is visible
     * @param notifications whether the notifications action is visible
     * @param settings whether the settings action is visible
     */
    public record Actions(
            String placement,
            Boolean balanceVisibility,
            Boolean notifications,
            Boolean settings) {}

    /** Creates the standard resting header with time-aware greeting and all three actions visible. */
    /** @return default resting greeting and action configuration */
    public static HomeRestingHeaderDTO defaults() {
        return new HomeRestingHeaderDTO(
                new Greeting("TIME_OF_DAY", true),
                new Actions("TRAILING", true, true, true));
    }
}
