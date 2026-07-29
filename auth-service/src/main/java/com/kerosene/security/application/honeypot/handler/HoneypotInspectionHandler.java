package com.kerosene.security.application.honeypot.handler;

import com.kerosene.security.application.honeypot.HoneypotInspectionContext;
import com.kerosene.security.domain.honeypot.HoneypotInspectionResult;

public interface HoneypotInspectionHandler {

    HoneypotInspectionResult handle(HoneypotInspectionContext context);
}
