package source.content.dto;

import java.util.Map;

/**
 * Communication Stage theater piece (home surface schema v2).
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

    public record Cta(String label, String action, String target) {}

    public record Content(
            String title,
            String body,
            String textMode,
            Map<String, Boolean> placeholders,
            Cta cta) {}

    public record Media(
            String type,
            String iconKey,
            String url,
            String posterUrl,
            Double aspectRatio,
            Boolean autoplay,
            Boolean muted,
            Boolean loop) {}

    public record ActionsLayout(String placement, String policy) {}

    public record Padding(Integer top, Integer bottom, Integer horizontal) {}

    /** One radial glow in the theater atmosphere. */
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

    public record Atmosphere(java.util.List<Glow> glows, Boolean animated, Integer transitionMs) {}

    public record Layout(
            String preset,
            String backgroundToken,
            Padding padding,
            Integer gap,
            Integer minHeight,
            Integer maxHeight,
            ActionsLayout actions,
            Atmosphere atmosphere) {}

    public record MotionStep(String type, Integer durationMs, String curve, Integer delayMs) {}

    public record BodyShift(
            Boolean enabled,
            Integer offsetPx,
            Integer durationMs,
            String curve,
            Boolean fadeBody) {}

    public record Motion(MotionStep enter, MotionStep exit, MotionStep content, BodyShift bodyShift) {}

    public record Lifecycle(
            Integer showDurationMs, Boolean restoreOnComplete, Boolean dismissible) {}

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

    public static Motion defaultMotion(int contentMs, int bodyOffset) {
        return new Motion(
                new MotionStep("FADE_SLIDE_DOWN", 420, "EASE_OUT_CUBIC", 0),
                new MotionStep("FADE", 280, "EASE_IN", 0),
                new MotionStep("MARQUEE", contentMs, "LINEAR", 0),
                new BodyShift(true, bodyOffset, 480, "EASE_OUT_CUBIC", false));
    }
}
