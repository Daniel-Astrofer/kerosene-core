package source.security.application.honeypot.handler;

import source.security.application.honeypot.HoneypotInspectionContext;
import source.security.domain.honeypot.HoneypotInspectionResult;

import java.util.Optional;

public class AllowRequestInspectionHandler extends AbstractHoneypotInspectionHandler {

    public AllowRequestInspectionHandler() {
        super(null);
    }

    @Override
    protected Optional<HoneypotInspectionResult> tryHandle(HoneypotInspectionContext context) {
        return Optional.of(HoneypotInspectionResult.forward());
    }
}
