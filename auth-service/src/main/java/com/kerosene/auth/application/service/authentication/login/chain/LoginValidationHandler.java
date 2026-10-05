package com.kerosene.auth.application.service.authentication.login.chain;

import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;
import com.kerosene.auth.application.service.common.chain.ChainHandler;

/** Contract for a handler in the ordered login validation chain. */
public interface LoginValidationHandler extends ChainHandler<LoginValidationContext> {
}
