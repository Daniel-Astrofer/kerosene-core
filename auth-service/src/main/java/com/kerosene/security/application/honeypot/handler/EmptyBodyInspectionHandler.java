package com.kerosene.security.application.honeypot.handler;

import com.kerosene.security.application.honeypot.HoneypotInspectionContext;
import com.kerosene.security.domain.honeypot.HoneypotInspectionResult;

import java.util.Optional;

public class EmptyBodyInspectionHandler extends AbstractHoneypotInspectionHandler {

    public EmptyBodyInspectionHandler(HoneypotInspectionHandler next) {
        super(next);
    }

    @Override
    protected Optional<HoneypotInspectionResult> tryHandle(HoneypotInspectionContext context) {
        if (!context.hasBody()) {
            return Optional.of(HoneypotInspectionResult.forward());
        }
        return Optional.empty();
    }
}
