package source.security.application.honeypot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import source.security.application.honeypot.HoneypotInspectionContext;
import source.security.application.honeypot.RequestJsonBodyParser;
import source.security.domain.honeypot.HoneypotInspectionResult;

import java.io.IOException;
import java.util.Optional;

public class JsonPayloadInspectionHandler extends AbstractHoneypotInspectionHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonPayloadInspectionHandler.class);

    private final RequestJsonBodyParser parser;

    public JsonPayloadInspectionHandler(RequestJsonBodyParser parser, HoneypotInspectionHandler next) {
        super(next);
        this.parser = parser;
    }

    @Override
    protected Optional<HoneypotInspectionResult> tryHandle(HoneypotInspectionContext context) {
        try {
            context.setParsedBody(parser.parse(context.body()));
            return Optional.empty();
        } catch (IOException e) {
            log.warn("[HONEYPOT] Malformed JSON rejected on path={}: {}", context.path(), e.getMessage());
            return Optional.of(HoneypotInspectionResult.malformedJson());
        }
    }
}
