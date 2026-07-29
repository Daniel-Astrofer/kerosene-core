package com.kerosene.security.application.honeypot;

import org.springframework.stereotype.Service;
import com.kerosene.security.application.honeypot.handler.AllowRequestInspectionHandler;
import com.kerosene.security.application.honeypot.handler.EmptyBodyInspectionHandler;
import com.kerosene.security.application.honeypot.handler.HoneypotFieldInspectionHandler;
import com.kerosene.security.application.honeypot.handler.HoneypotInspectionHandler;
import com.kerosene.security.application.honeypot.handler.JsonPayloadInspectionHandler;
import com.kerosene.security.domain.honeypot.HoneypotInspectionResult;

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
