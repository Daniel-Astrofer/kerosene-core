package source.common.infra.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Emits sanitized startup diagnostics for successful and failed Spring boot lifecycles.
 */
@Component
public class StartupDiagnosticsListener {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnosticsListener.class);
    private final StartupDiagnosticsService startupDiagnosticsService;

    public StartupDiagnosticsListener(StartupDiagnosticsService startupDiagnosticsService) {
        this.startupDiagnosticsService = startupDiagnosticsService;
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        StartupDiagnosticReport report = startupDiagnosticsService.diagnose();
        log.info("STARTUP_DIAGNOSTICS event=STARTUP_READY {}", report.toLogSummary());
    }

    @EventListener
    public void onAvailabilityChange(AvailabilityChangeEvent<?> event) {
        log.info("Application availability changed: state={}", event.getState());
    }

    @EventListener
    public void onFailed(ApplicationFailedEvent event) {
        Throwable failure = event.getException();
        StartupDiagnosticReport report = startupDiagnosticsService.diagnose();
        log.error("STARTUP_DIAGNOSTICS event=STARTUP_FAILED failureType={} failureMessage={} {}",
                failure.getClass().getSimpleName(),
                sanitizeFailureMessage(failure.getMessage()),
                report.toLogSummary(),
                failure);
    }

    private String sanitizeFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.replaceAll("(?i)(password|secret|token|macaroon|private-key|api-key)=\\S+", "$1=<redacted>");
    }
}
