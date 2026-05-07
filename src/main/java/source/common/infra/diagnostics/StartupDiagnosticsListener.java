package source.common.infra.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupDiagnosticsListener {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnosticsListener.class);

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        log.info("Application startup completed and HTTP readiness probes are available");
    }

    @EventListener
    public void onAvailabilityChange(AvailabilityChangeEvent<?> event) {
        log.info("Application availability changed: state={}", event.getState());
    }

    @EventListener
    public void onFailed(ApplicationFailedEvent event) {
        Throwable failure = event.getException();
        log.error("Application startup failed: type={} message={}",
                failure.getClass().getSimpleName(),
                failure.getMessage(),
                failure);
    }
}
