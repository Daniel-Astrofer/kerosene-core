package com.kerosene.auth.application.service.authentication.signup.chain;

import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;
import com.kerosene.auth.application.service.common.chain.ChainHandler;

/** Contract for one ordered signup validation step. */
public interface SignupValidationHandler extends ChainHandler<SignupValidationContext> {
}
