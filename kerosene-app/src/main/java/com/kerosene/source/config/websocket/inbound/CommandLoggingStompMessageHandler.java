package source.config.websocket.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;

public class CommandLoggingStompMessageHandler extends AbstractStompMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandLoggingStompMessageHandler.class);

    public CommandLoggingStompMessageHandler(StompMessageHandler next) {
        super(next);
    }

    @Override
    public Message<?> handle(StompMessageContext context) {
        if (context.command() != null) {
            log.debug("[STOMP-IN] {} | Session: {} | User: {}",
                    context.command(),
                    context.accessor().getSessionId(),
                    context.accessor().getUser() != null ? context.accessor().getUser().getName() : "UNAUTHENTICATED");
        }
        return passToNext(context);
    }
}
