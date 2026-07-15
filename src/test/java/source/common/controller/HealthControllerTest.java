package source.common.controller;

import org.junit.jupiter.api.Test;
import source.common.infra.health.DependencyHealth;
import source.common.infra.health.OperationalHealthService;
import source.common.infra.health.OperationalHealthSnapshot;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final OperationalHealthService healthService = mock(OperationalHealthService.class);
    private final HealthController controller = new HealthController(healthService);

    @Test
    void liveReturnsOkWhenProcessIsUp() {
        when(healthService.liveness()).thenReturn(snapshot("UP"));

        assertEquals(200, controller.live().getStatusCode().value());
    }

    @Test
    void readyReturnsServiceUnavailableWhenCriticalDependencyIsDown() {
        when(healthService.publicReadiness()).thenReturn(snapshot("DOWN"));

        assertEquals(503, controller.ready().getStatusCode().value());
    }

    @Test
    void dependenciesReturnsDetailedStatusCode() {
        when(healthService.dependencies()).thenReturn(snapshot("DEGRADED"));

        assertEquals(200, controller.dependencies().getStatusCode().value());
    }

    private OperationalHealthSnapshot snapshot(String status) {
        return new OperationalHealthSnapshot(
                status,
                "kerosene",
                "DEV",
                Instant.now(),
                Map.of("probe", new DependencyHealth("probe", status, true, 1, "test", Map.of())));
    }
}
