package source.common.infra.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HealthMetricRecorder {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, AtomicInteger> dependencyStatuses = new ConcurrentHashMap<>();

    public HealthMetricRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(DependencyHealth health) {
        AtomicInteger status = dependencyStatuses.computeIfAbsent(health.name(), this::registerStatusGauge);
        status.set(health.isUp() ? 1 : 0);

        Timer.builder("kerosene.dependency.health.latency")
                .description("Dependency health check latency")
                .tag("dependency", health.name())
                .register(registry)
                .record(Math.max(0, health.latencyMs()), TimeUnit.MILLISECONDS);
    }

    private AtomicInteger registerStatusGauge(String dependency) {
        AtomicInteger value = new AtomicInteger(0);
        Gauge.builder("kerosene.dependency.health.status", value, AtomicInteger::get)
                .description("Dependency health status where 1 is UP and 0 is not UP")
                .tag("dependency", dependency)
                .register(registry);
        return value;
    }
}
