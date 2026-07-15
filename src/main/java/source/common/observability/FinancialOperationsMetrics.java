package source.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FinancialOperationsMetrics {

    private final MeterRegistry meterRegistry;

    public FinancialOperationsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void increment(String name, String outcome) {
        Counter.builder("kerosene.financial." + name)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void increment(String name, String outcome, String type) {
        Counter.builder("kerosene.financial." + name)
                .tag("outcome", outcome)
                .tag("type", type != null ? type : "unknown")
                .register(meterRegistry)
                .increment();
    }
}
