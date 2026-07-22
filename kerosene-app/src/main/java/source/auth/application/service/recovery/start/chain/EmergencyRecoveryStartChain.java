package source.auth.application.service.recovery.start.chain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;
import source.auth.dto.EmergencyRecoveryStartRequest;

@Component
public class EmergencyRecoveryStartChain {

    private final EmergencyRecoveryStartHandler firstHandler;

    public EmergencyRecoveryStartChain(List<EmergencyRecoveryStartHandler> handlers) {
        List<EmergencyRecoveryStartHandler> orderedHandlers = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(orderedHandlers);
        this.firstHandler = linkHandlers(orderedHandlers);
    }

    public EmergencyRecoveryStartContext handle(EmergencyRecoveryStartRequest request, String clientFingerprint) {
        EmergencyRecoveryStartContext context = new EmergencyRecoveryStartContext(request, clientFingerprint);
        if (firstHandler != null) {
            firstHandler.handle(context);
        }
        return context;
    }

    private EmergencyRecoveryStartHandler linkHandlers(List<EmergencyRecoveryStartHandler> handlers) {
        for (int i = 0; i < handlers.size() - 1; i++) {
            handlers.get(i).setNext(handlers.get(i + 1));
        }
        return handlers.isEmpty() ? null : handlers.get(0);
    }
}
