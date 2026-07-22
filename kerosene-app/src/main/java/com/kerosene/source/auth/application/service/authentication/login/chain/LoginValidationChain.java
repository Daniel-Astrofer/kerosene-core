package source.auth.application.service.authentication.login.chain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.login.LoginValidationContext;

@Component
public class LoginValidationChain {

    private final LoginValidationHandler firstHandler;

    public LoginValidationChain(List<LoginValidationHandler> handlers) {
        List<LoginValidationHandler> orderedHandlers = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(orderedHandlers);
        this.firstHandler = linkHandlers(orderedHandlers);
    }

    public void validate(LoginValidationContext context) {
        if (firstHandler != null) {
            firstHandler.handle(context);
        }
    }

    private LoginValidationHandler linkHandlers(List<LoginValidationHandler> handlers) {
        for (int i = 0; i < handlers.size() - 1; i++) {
            handlers.get(i).setNext(handlers.get(i + 1));
        }
        return handlers.isEmpty() ? null : handlers.get(0);
    }
}
