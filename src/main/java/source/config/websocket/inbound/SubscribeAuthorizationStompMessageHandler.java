package source.config.websocket.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;

public class SubscribeAuthorizationStompMessageHandler extends AbstractStompMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SubscribeAuthorizationStompMessageHandler.class);

    public SubscribeAuthorizationStompMessageHandler(StompMessageHandler next) {
        super(next);
    }

    @Override
    public Message<?> handle(StompMessageContext context) {
        if (!StompCommand.SUBSCRIBE.equals(context.command())) {
            return passToNext(context);
        }

        if (context.accessor().getUser() == null) {
            log.warn("[STOMP-AUTH] Unauthorized SUBSCRIBE to: {}", context.accessor().getDestination());
            throw new MessageDeliveryException("Unauthorized: Please connect with a valid JWT token");
        }

        return passToNext(context);
    }
}
