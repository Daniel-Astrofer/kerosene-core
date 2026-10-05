package com.kerosene.auth.application.service.authentication.signup.chain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;

/** Sorts signup handlers by Spring order, links them, and executes the validation pipeline. */
@Component
public class SignupValidationChain {

    /** First handler in the ordered signup pipeline, or null when none are configured. */
    private final SignupValidationHandler firstHandler;

    /** Sorts and links the required-fields, username-policy, passphrase-policy, and availability handlers. */
    /** @param handlers Spring-provided signup handlers */
    public SignupValidationChain(List<SignupValidationHandler> handlers) {
        List<SignupValidationHandler> orderedHandlers = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(orderedHandlers);
        this.firstHandler = linkHandlers(orderedHandlers);
    }

    /** Starts the linked pipeline when at least one handler is configured. */
    /** @param context request values checked by the handlers */
    public void validate(SignupValidationContext context) {
        if (firstHandler != null) {
            firstHandler.handle(context);
        }
    }

    /** Connects adjacent ordered signup handlers and returns the first step. */
    /** @param handlers handlers sorted by {@code @Order} */
    /** @return first handler, or null for an empty list */
    private SignupValidationHandler linkHandlers(List<SignupValidationHandler> handlers) {
        for (int i = 0; i < handlers.size() - 1; i++) {
            handlers.get(i).setNext(handlers.get(i + 1));
        }
        return handlers.isEmpty() ? null : handlers.get(0);
    }
}
