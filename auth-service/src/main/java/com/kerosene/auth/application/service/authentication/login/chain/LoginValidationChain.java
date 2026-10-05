package com.kerosene.auth.application.service.authentication.login.chain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;

/** Sorts login handlers by Spring order, links them, and starts each validation request. */
@Component
public class LoginValidationChain {

    /** First handler in the ordered login pipeline, or null when no handlers are configured. */
    private final LoginValidationHandler firstHandler;

    /** Sorts and links handlers so request, throttle, lookup, and passphrase checks run in order. */
    /** @param handlers all Spring-provided login handlers */
    public LoginValidationChain(List<LoginValidationHandler> handlers) {
        List<LoginValidationHandler> orderedHandlers = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(orderedHandlers);
        this.firstHandler = linkHandlers(orderedHandlers);
    }

    /** Starts the linked chain when at least one handler is configured. */
    /** @param context mutable request context shared by handlers */
    public void validate(LoginValidationContext context) {
        if (firstHandler != null) {
            firstHandler.handle(context);
        }
    }

    /** Connects adjacent ordered handlers and returns the chain's first element. */
    /** @param handlers handlers sorted by {@code @Order} */
    /** @return first handler, or null for an empty list */
    private LoginValidationHandler linkHandlers(List<LoginValidationHandler> handlers) {
        for (int i = 0; i < handlers.size() - 1; i++) {
            handlers.get(i).setNext(handlers.get(i + 1));
        }
        return handlers.isEmpty() ? null : handlers.get(0);
    }
}
