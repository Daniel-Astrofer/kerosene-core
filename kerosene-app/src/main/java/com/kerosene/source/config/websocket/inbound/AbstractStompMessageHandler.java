package source.config.websocket.inbound;

import org.springframework.messaging.Message;

public abstract class AbstractStompMessageHandler implements StompMessageHandler {

    private final StompMessageHandler next;

    protected AbstractStompMessageHandler(StompMessageHandler next) {
        this.next = next;
    }

    protected Message<?> passToNext(StompMessageContext context) {
        if (next == null) {
            return context.message();
        }
        return next.handle(context);
    }
}
