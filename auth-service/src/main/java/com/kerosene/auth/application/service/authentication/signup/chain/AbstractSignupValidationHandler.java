package com.kerosene.auth.application.service.authentication.signup.chain;

import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;
import com.kerosene.auth.application.service.common.chain.AbstractChainHandler;

public abstract class AbstractSignupValidationHandler extends AbstractChainHandler<SignupValidationContext>
        implements SignupValidationHandler {
}
