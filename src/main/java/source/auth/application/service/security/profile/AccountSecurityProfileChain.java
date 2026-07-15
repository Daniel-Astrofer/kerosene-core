package source.auth.application.service.security.profile;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
public class AccountSecurityProfileChain {

    private final AccountSecurityProfileHandler firstHandler;

    public AccountSecurityProfileChain(List<AccountSecurityProfileHandler> handlers) {
        List<AccountSecurityProfileHandler> orderedHandlers = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(orderedHandlers);
        this.firstHandler = linkHandlers(orderedHandlers);
    }

    public void normalize(AccountSecurityProfileContext context) {
        if (firstHandler != null) {
            firstHandler.handle(context);
        }
    }

    private AccountSecurityProfileHandler linkHandlers(List<AccountSecurityProfileHandler> handlers) {
        for (int i = 0; i < handlers.size() - 1; i++) {
            handlers.get(i).setNext(handlers.get(i + 1));
        }
        return handlers.isEmpty() ? null : handlers.get(0);
    }
}
