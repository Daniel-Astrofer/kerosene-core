package source.security.application.honeypot;

import org.springframework.stereotype.Service;
import source.security.application.honeypot.handler.AllowRequestInspectionHandler;
import source.security.application.honeypot.handler.EmptyBodyInspectionHandler;
import source.security.application.honeypot.handler.HoneypotFieldInspectionHandler;
import source.security.application.honeypot.handler.HoneypotInspectionHandler;
import source.security.application.honeypot.handler.JsonPayloadInspectionHandler;
import source.security.domain.honeypot.HoneypotInspectionResult;

@Service
public class HoneypotInspectionUseCase {

    private final HoneypotInspectionHandler inspectionChain;

    public HoneypotInspectionUseCase(RequestJsonBodyParser parser) {
        this.inspectionChain = new EmptyBodyInspectionHandler(
                new JsonPayloadInspectionHandler(
                        parser,
                        new HoneypotFieldInspectionHandler(
                                new AllowRequestInspectionHandler())));
    }

    public HoneypotInspectionResult inspect(HoneypotInspectionCommand command) {
        return inspectionChain.handle(new HoneypotInspectionContext(command));
    }
}
