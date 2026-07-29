package com.kerosene.security.application.honeypot.handler;

import com.kerosene.security.application.honeypot.HoneypotInspectionContext;
import com.kerosene.security.domain.honeypot.HoneypotInspectionResult;

import java.util.Optional;

public abstract class AbstractHoneypotInspectionHandler implements HoneypotInspectionHandler {

    private final HoneypotInspectionHandler next;

    protected AbstractHoneypotInspectionHandler(HoneypotInspectionHandler next) {
        this.next = next;
    }

    @Override
    public final HoneypotInspectionResult handle(HoneypotInspectionContext context) {
        Optional<HoneypotInspectionResult> currentDecision = tryHandle(context);
        if (currentDecision.isPresent()) {
            return currentDecision.get();
        }
        if (next == null) {
            return HoneypotInspectionResult.forward();
        }
        return next.handle(context);
    }

    protected abstract Optional<HoneypotInspectionResult> tryHandle(HoneypotInspectionContext context);
}
