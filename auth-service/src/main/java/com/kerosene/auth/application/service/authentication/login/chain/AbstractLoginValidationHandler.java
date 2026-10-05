package com.kerosene.auth.application.service.authentication.login.chain;

import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;
import com.kerosene.auth.application.service.common.chain.AbstractChainHandler;

/** Shared chain-link implementation for login validation steps. */
public abstract class AbstractLoginValidationHandler extends AbstractChainHandler<LoginValidationContext>
        implements LoginValidationHandler {
}
