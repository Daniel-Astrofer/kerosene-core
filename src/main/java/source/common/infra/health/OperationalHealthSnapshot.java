package source.common.infra.health;

import java.time.Instant;
import java.util.Map;

public record OperationalHealthSnapshot(
        String status,
        String service,
        String region,
        Instant timestamp,
        Map<String, DependencyHealth> checks) {
}
