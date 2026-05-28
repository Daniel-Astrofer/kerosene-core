package source.common.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.infra.health.OperationalHealthService;
import source.common.infra.health.OperationalHealthSnapshot;

@RestController
public class HealthController {

    private final OperationalHealthService healthService;

    public HealthController(OperationalHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health/live")
    public ResponseEntity<OperationalHealthSnapshot> live() {
        return response(healthService.liveness());
    }

    @GetMapping("/health/ready")
    public ResponseEntity<OperationalHealthSnapshot> ready() {
        return response(healthService.publicReadiness());
    }

    @GetMapping("/health/dependencies")
    public ResponseEntity<OperationalHealthSnapshot> dependencies() {
        return response(healthService.dependencies());
    }

    private ResponseEntity<OperationalHealthSnapshot> response(OperationalHealthSnapshot snapshot) {
        HttpStatus status = "DOWN".equals(snapshot.status())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(snapshot);
    }
}
