package source.common.infra.diagnostics;

import java.util.List;

/**
 * Immutable result for one startup diagnostic check. Details must contain only property names,
 * sanitized metadata, or non-secret policy violations.
 */
public class StartupDiagnosticCheck {

    private final StartupDiagnosticStatus status;
    private final String name;
    private final String message;
    private final List<String> details;

    public StartupDiagnosticCheck(
            StartupDiagnosticStatus status,
            String name,
            String message,
            List<String> details) {
        this.status = status;
        this.name = name;
        this.message = message;
        this.details = List.copyOf(details);
    }

    public StartupDiagnosticStatus status() {
        return status;
    }

    public String name() {
        return name;
    }

    public String message() {
        return message;
    }

    public List<String> details() {
        return details;
    }
}
