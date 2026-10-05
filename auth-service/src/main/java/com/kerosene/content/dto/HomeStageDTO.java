package com.kerosene.content.dto;

import java.util.Map;

/**
 * A stage in the schema-v2 home communication theater, combining content, presentation, motion, and lifecycle rules.
 * @param id stable stage identifier consumed by client state and analytics
 * @param kind stage category, such as IDLE or a server-published communication
 * @param playPolicy rule that controls how often the stage may be shown
 * @param priority relative ordering used when selecting a stage
 * @param content text, placeholders, and call to action shown by the stage
 * @param media icon or remote media configuration
 * @param layout spatial preset, spacing, sizing, and action placement
 * @param motion entrance, exit, content, and body-shift animation settings
 * @param lifecycle duration and user-dismissal behavior
 * @param atmosphere background glows and transition treatment
 */
public record HomeStageDTO(
        String id,
        String kind,
        String playPolicy,
        int priority,
        Content content,
        Media media,
        Layout layout,
        Motion motion,
        Lifecycle lifecycle,
        Atmosphere atmosphere) {

    /**
     * Optional interactive action attached to stage content.
     * @param label user-visible action label
     * @param action client action identifier
     * @param target optional route or action target interpreted by the client
     */
    public record Cta(String label, String action, String target) {}

    /**
     * Text and action content rendered within the stage.
     * @param title optional heading
     * @param body body text or template
     * @param textMode rendering mode, such as STATIC or a client-supported animation
     * @param placeholders named substitution flags available to the content template
     * @param cta optional call to action
     */
    public record Content(
            String title,
            String body,
            String textMode,
            Map<String, Boolean> placeholders,
            Cta cta) {}

    /**
     * Optional visual media associated with the stage.
     * @param type media category, including NONE when no media should render
     * @param iconKey symbolic icon identifier resolved by the client
     * @param url media resource URL
     * @param posterUrl preview image URL for media that needs a poster
     * @param aspectRatio desired width-to-height ratio
     * @param autoplay whether playback may start automatically
     * @param muted whether media audio must remain muted
     * @param loop whether media repeats after completion
     */
    public record Media(
            String type,
            String iconKey,
            String url,
            String posterUrl,
            Double aspectRatio,
            Boolean autoplay,
            Boolean muted,
            Boolean loop) {}

    /**
     * Position and visibility policy for stage actions.
     * @param placement location of the action group within the layout
     * @param policy rule controlling when the action group is visible
     */
    public record ActionsLayout(String placement, String policy) {}

    /** Insets applied to the content surface.
     * @param top top inset in logical pixels
     * @param bottom bottom inset in logical pixels
     * @param horizontal shared left and right inset in logical pixels
     */
    public record Padding(Integer top, Integer bottom, Integer horizontal) {}

    /**
     * One radial glow in the theater atmosphere.
     * @param id stable identifier for deterministic client rendering
     * @param colorToken design-system color token
     * @param x horizontal center coordinate in normalized layout space
     * @param y vertical center coordinate in normalized layout space
     * @param width glow width in normalized layout space
     * @param height glow height in normalized layout space
     * @param intensity visual opacity or strength
     * @param radius blur radius used by the renderer
     * @param zIndex paint order relative to other atmosphere layers
     */
    public record Glow(
            String id,
            String colorToken,
            Double x,
            Double y,
            Double width,
            Double height,
            Double intensity,
            Double radius,
            Integer zIndex) {}

    /**
     * Background atmosphere made from radial glows and an optional transition.
     * @param glows ordered glow layers to render
     * @param animated whether atmosphere changes may animate
     * @param transitionMs transition duration in milliseconds
     */
    public record Atmosphere(java.util.List<Glow> glows, Boolean animated, Integer transitionMs) {}

    /**
     * Spatial and sizing rules for stage content and actions.
     * @param preset named client layout preset
     * @param backgroundToken design-system token for the stage background
     * @param padding content insets
     * @param gap spacing between layout children in logical pixels
     * @param minHeight minimum rendered height in logical pixels
     * @param maxHeight maximum rendered height in logical pixels
     * @param actions action placement and visibility rules
     * @param atmosphere background glow composition for this layout
     */
    public record Layout(
            String preset,
            String backgroundToken,
            Padding padding,
            Integer gap,
            Integer minHeight,
            Integer maxHeight,
            ActionsLayout actions,
            Atmosphere atmosphere) {}

    /**
     * One timed animation operation.
     * @param type client animation primitive, such as FADE or MARQUEE
     * @param durationMs animation duration
     * @param curve interpolation curve name understood by the client
     * @param delayMs wait before the operation starts
     */
    public record MotionStep(String type, Integer durationMs, String curve, Integer delayMs) {}

    /**
     * Optional vertical body offset applied during stage transitions.
     * @param enabled whether the body shift is active
     * @param offsetPx vertical offset in logical pixels
     * @param durationMs shift transition duration
     * @param curve interpolation curve name
     * @param fadeBody whether the body should fade while shifting
     */
    public record BodyShift(
            Boolean enabled,
            Integer offsetPx,
            Integer durationMs,
            String curve,
            Boolean fadeBody) {}

    /**
     * Animation sequence for entering, exiting, and changing stage content.
     * @param enter entrance animation
     * @param exit exit animation
     * @param content animation applied to changing or scrolling content
     * @param bodyShift coordinated body offset during transition
     */
    public record Motion(MotionStep enter, MotionStep exit, MotionStep content, BodyShift bodyShift) {}

    /**
     * Duration and dismissal behavior for a stage.
     * @param showDurationMs display duration before completion
     * @param restoreOnComplete whether completion restores the prior surface state
     * @param dismissible whether a user may dismiss the stage manually
     */
    public record Lifecycle(
            Integer showDurationMs, Boolean restoreOnComplete, Boolean dismissible) {}

    /** Creates the stable neutral stage used when no communication is active. */
    /** @return idle composition with default compact layout and motion settings */
    public static HomeStageDTO idle() {
        return new HomeStageDTO(
                "idle",
                "IDLE",
                "ONCE",
                0,
                new Content("", null, "STATIC", Map.of("name", false), null),
                new Media("NONE", null, null, null, 1.0, false, true, false),
                new Layout(
                        "COMPACT_LINE",
                        "transparent",
                        new Padding(0, 0, 0),
                        8,
                        0,
                        48,
                        new ActionsLayout("TRAILING", "ALWAYS_VISIBLE"),
                        new Atmosphere(java.util.List.of(), true, 480)),
                defaultMotion(9000, 36),
                new Lifecycle(0, true, false),
                new Atmosphere(java.util.List.of(), true, 480));
    }

    /** Builds the baseline animation sequence while allowing content duration and body offset to vary. */
    /** @param contentMs duration of the content animation in milliseconds */
    /** @param bodyOffset vertical body-shift offset in logical pixels */
    /** @return composed entrance, exit, content, and body-shift motion */
    public static Motion defaultMotion(int contentMs, int bodyOffset) {
        return new Motion(
                new MotionStep("FADE_SLIDE_DOWN", 420, "EASE_OUT_CUBIC", 0),
                new MotionStep("FADE", 280, "EASE_IN", 0),
                new MotionStep("MARQUEE", contentMs, "LINEAR", 0),
                new BodyShift(true, bodyOffset, 480, "EASE_OUT_CUBIC", false));
    }
}
