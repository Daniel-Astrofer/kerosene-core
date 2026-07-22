package source.common.financial;

import java.util.Map;

public interface FinancialNotificationAuditPort {

    void recordDeviceTokenEvent(String eventType, Map<String, ?> redactedPayload);
}
