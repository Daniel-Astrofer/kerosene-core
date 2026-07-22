package source.security.application.honeypot.handler;

import source.security.application.honeypot.HoneypotInspectionContext;
import source.security.domain.honeypot.HoneypotInspectionResult;

public interface HoneypotInspectionHandler {

    HoneypotInspectionResult handle(HoneypotInspectionContext context);
}
