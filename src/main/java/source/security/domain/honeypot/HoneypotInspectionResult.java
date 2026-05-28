package source.security.domain.honeypot;

public record HoneypotInspectionResult(
        HoneypotInspectionOutcome outcome,
        int httpStatus,
        String clientMessage) {

    public static HoneypotInspectionResult forward() {
        return new HoneypotInspectionResult(HoneypotInspectionOutcome.FORWARD, 0, null);
    }

    public static HoneypotInspectionResult blackhole() {
        return new HoneypotInspectionResult(HoneypotInspectionOutcome.BLACKHOLE, 200, "OK");
    }

    public static HoneypotInspectionResult malformedJson() {
        return new HoneypotInspectionResult(HoneypotInspectionOutcome.REJECT, 400, "Malformed JSON payload.");
    }

    public boolean shouldContinueFilterChain() {
        return outcome == HoneypotInspectionOutcome.FORWARD;
    }
}
