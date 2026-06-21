package source.common.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredAuditLoggerTest {

    @Test
    void taxonomyContainsQueueMinimumAuditEvents() {
        assertThat(AuditEventType.requireKnown("AUTH_LOGIN_SUCCEEDED")).isEqualTo(AuditEventType.AUTH_LOGIN_SUCCEEDED);
        assertThat(AuditEventType.requireKnown("KFE_SETTLEMENT_COMPLETED")).isEqualTo(AuditEventType.KFE_SETTLEMENT_COMPLETED);
        assertThat(AuditEventType.requireKnown("MPC_UNSUPPORTED_MODE_REJECTED"))
                .isEqualTo(AuditEventType.MPC_UNSUPPORTED_MODE_REJECTED);
    }

    @Test
    void taxonomyRejectsUnknownEventNames() {
        assertThatThrownBy(() -> AuditEventType.requireKnown("KFE_NOT_A_REAL_EVENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown audit event type");
    }

    @Test
    void sanitizerMasksSecretsAndSummarizesRawPayloadShapes() {
        Map<String, Object> sanitized = AuditEventPayloadSanitizer.sanitize(Map.of(
                "token", "secret-token",
                "invoice", "lnbc1verylongpaymentrequestpayloadthatmustneverappear",
                "requestBody", "{\"password\":\"clear\"}",
                "reason", "failed for user@example.com",
                "metadata", Map.of("privateKey", "abc")));

        assertThat(sanitized)
                .containsEntry("token", "[MASKED]")
                .containsEntry("invoice", "[MASKED]")
                .containsEntry("requestBody", "[MASKED]")
                .containsEntry("metadata", "map(size=1)");
        assertThat(sanitized.get("reason")).isEqualTo("failed for u***r@example.com");
    }
}
