package source.notification.model;

public enum NotificationSeverity {
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error");

    private final String wireValue;

    NotificationSeverity(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static NotificationSeverity fromValue(String value) {
        if (value == null || value.isBlank()) {
            return INFO;
        }

        for (NotificationSeverity severity : values()) {
            if (severity.wireValue.equalsIgnoreCase(value.trim())) {
                return severity;
            }
        }

        return INFO;
    }
}
