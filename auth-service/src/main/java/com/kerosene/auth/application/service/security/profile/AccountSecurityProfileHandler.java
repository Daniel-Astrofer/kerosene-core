package com.kerosene.auth.application.service.security.profile;

import com.kerosene.auth.application.service.common.chain.ChainHandler;

/** Specialized chain contract for validating or normalizing account security profile settings. */
public interface AccountSecurityProfileHandler extends ChainHandler<AccountSecurityProfileContext> {
}
