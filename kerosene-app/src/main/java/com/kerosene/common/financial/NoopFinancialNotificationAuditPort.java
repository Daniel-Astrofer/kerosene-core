package source.common.financial;

import java.util.Map;

public class NoopFinancialNotificationAuditPort implements FinancialNotificationAuditPort {

    @Override
    public void recordDeviceTokenEvent(String eventType, Map<String, ?> redactedPayload) {
        // Intentionally empty. KFE provides durable financial audit when available.
    }
}
