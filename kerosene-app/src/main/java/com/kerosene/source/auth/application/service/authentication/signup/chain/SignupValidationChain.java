package source.auth.application.service.authentication.signup.chain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.signup.SignupValidationContext;

@Component
public class SignupValidationChain {

    private final SignupValidationHandler firstHandler;

    public SignupValidationChain(List<SignupValidationHandler> handlers) {
        List<SignupValidationHandler> orderedHandlers = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(orderedHandlers);
        this.firstHandler = linkHandlers(orderedHandlers);
    }

    public void validate(SignupValidationContext context) {
        if (firstHandler != null) {
            firstHandler.handle(context);
        }
    }

    private SignupValidationHandler linkHandlers(List<SignupValidationHandler> handlers) {
        for (int i = 0; i < handlers.size() - 1; i++) {
            handlers.get(i).setNext(handlers.get(i + 1));
        }
        return handlers.isEmpty() ? null : handlers.get(0);
    }
}
