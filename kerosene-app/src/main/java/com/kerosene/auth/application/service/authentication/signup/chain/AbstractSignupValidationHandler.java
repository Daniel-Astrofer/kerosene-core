package source.auth.application.service.authentication.signup.chain;

import source.auth.application.service.authentication.signup.SignupValidationContext;
import source.auth.application.service.common.chain.AbstractChainHandler;

public abstract class AbstractSignupValidationHandler extends AbstractChainHandler<SignupValidationContext>
        implements SignupValidationHandler {
}
