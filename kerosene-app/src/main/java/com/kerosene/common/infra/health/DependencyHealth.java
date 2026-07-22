package source.common.infra.health;

import java.util.Map;

public record DependencyHealth(
        String name,
        String status,
        boolean critical,
        long latencyMs,
        String message,
        Map<String, Object> details) {

    public boolean isUp() {
        return "UP".equals(status);
    }

    public boolean isDown() {
        return "DOWN".equals(status);
    }
}
