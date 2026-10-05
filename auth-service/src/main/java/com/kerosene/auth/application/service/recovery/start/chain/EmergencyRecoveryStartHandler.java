package com.kerosene.auth.application.service.recovery.start.chain;

import com.kerosene.auth.application.service.common.chain.ChainHandler;
import com.kerosene.auth.application.service.recovery.start.EmergencyRecoveryStartContext;

/** Contract for one ordered emergency-recovery start proof or eligibility check. */
public interface EmergencyRecoveryStartHandler extends ChainHandler<EmergencyRecoveryStartContext> {
}
