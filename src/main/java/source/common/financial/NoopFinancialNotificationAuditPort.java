package source.common.financial;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnMissingBean(FinancialNotificationAuditPort.class)
public class NoopFinancialNotificationAuditPort implements FinancialNotificationAuditPort {

    @Override
    public void recordDeviceTokenEvent(String eventType, Map<String, ?> redactedPayload) {
        // Intentionally empty. KFE provides durable financial audit when available.
    }
}
