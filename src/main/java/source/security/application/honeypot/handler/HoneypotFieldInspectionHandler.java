package source.security.application.honeypot.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import source.common.infra.logging.LogSanitizer;
import source.security.application.honeypot.HoneypotInspectionContext;
import source.security.domain.honeypot.HoneypotInspectionResult;

import java.util.Optional;

public class HoneypotFieldInspectionHandler extends AbstractHoneypotInspectionHandler {

    private static final Logger log = LoggerFactory.getLogger(HoneypotFieldInspectionHandler.class);
    private static final String HONEYPOT_FIELD = "__hp";

    public HoneypotFieldInspectionHandler(HoneypotInspectionHandler next) {
        super(next);
    }

    @Override
    protected Optional<HoneypotInspectionResult> tryHandle(HoneypotInspectionContext context) {
        JsonNode payload = context.parsedBody();
        if (payload == null) {
            return Optional.empty();
        }

        JsonNode honeypotField = payload.get(HONEYPOT_FIELD);
        if (honeypotField != null && !honeypotField.isNull() && !honeypotField.asText("").isBlank()) {
            log.warn("[HONEYPOT] Triggered from ip={} path={} userAgentRef={}",
                    LogSanitizer.maskedIp(context.remoteAddress()),
                    context.path(),
                    LogSanitizer.fingerprint(context.userAgent()));
            return Optional.of(HoneypotInspectionResult.blackhole());
        }

        return Optional.empty();
    }
}
