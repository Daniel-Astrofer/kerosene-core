package source.config.websocket.inbound;

import org.springframework.messaging.Message;

public interface StompMessageHandler {

    Message<?> handle(StompMessageContext context);
}
