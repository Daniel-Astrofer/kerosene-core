package source.common.observability;

import com.fasterxml.jackson.core.JsonStreamContext;
import net.logstash.logback.mask.ValueMasker;
import source.common.infra.logging.LogSanitizer;

/**
 * Logback value masker plugged into {@code MaskingJsonGeneratorDecorator}.
 *
 * <p>Two-pass masking strategy:
 * <ol>
 *   <li>If the current JSON <em>key</em> is in the sensitive-key set, the entire value is
 *       replaced with {@code [MASKED]} — no partial reveal.</li>
 *   <li>If the value is a string, it goes through the full
 *       {@link LogSanitizer#sanitizeFinancialPayload} pipeline to scrub
 *       inline secrets, PII, tokens, and Bitcoin/Lightning artefacts.</li>
 * </ol>
 *
 * <p>All masking logic lives in {@link LogSanitizer} — this class is only
 * the Logback integration bridge.
 */
public class SensitiveDataMasker implements ValueMasker {

    @Override
    public Object mask(JsonStreamContext context, Object value) {
        if (value == null) {
            return null;
        }

        // Key-based masking: sensitive field names → blanket mask
        String fieldName = context != null ? context.getCurrentName() : null;
        if (LogSanitizer.isSensitiveKey(fieldName)) {
            return "[MASKED]";
        }

        // Value-based masking: scrub inline secrets / PII from string values
        if (value instanceof CharSequence) {
            return LogSanitizer.sanitizeFinancialPayload(value.toString());
        }

        return value;
    }
}
