package source.auth.application.service.authentication.login.chain;

import source.auth.application.service.authentication.login.LoginValidationContext;
import source.auth.application.service.common.chain.AbstractChainHandler;

public abstract class AbstractLoginValidationHandler extends AbstractChainHandler<LoginValidationContext>
        implements LoginValidationHandler {
}
