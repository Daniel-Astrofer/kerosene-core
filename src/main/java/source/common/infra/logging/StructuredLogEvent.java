package source.common.infra.logging;

import net.logstash.logback.argument.StructuredArgument;

import java.util.ArrayList;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Small builder for safe structured log fields.
 *
 * <p>The builder sanitizes string values as a last line of defense. Callers are
 * still responsible for passing only metadata, never bodies or raw secrets.
 */
public final class StructuredLogEvent {

    private final List<StructuredArgument> fields = new ArrayList<>();

    private StructuredLogEvent(String event, String domain, String operation, String safeMessage) {
        field(StructuredLogField.EVENT, event);
        field(StructuredLogField.DOMAIN, domain);
        field(StructuredLogField.OPERATION, operation);
        field(StructuredLogField.MESSAGE, safeMessage);
    }

    public static StructuredLogEvent of(String event, String domain, String operation, String safeMessage) {
        return new StructuredLogEvent(event, domain, operation, safeMessage);
    }

    public StructuredLogEvent field(String name, Object value) {
        if (name != null && value != null) {
            fields.add(kv(name, sanitize(value)));
        }
        return this;
    }

    public StructuredLogEvent exception(Throwable throwable) {
        if (throwable != null) {
            field(StructuredLogField.EXCEPTION_TYPE, throwable.getClass().getSimpleName());
        }
        return this;
    }

    public Object[] arguments() {
        return fields.toArray();
    }

    private Object sanitize(Object value) {
        if (value instanceof String text) {
            return LogSanitizer.sanitizeFinancialPayload(text);
        }
        return value;
    }
}
