package source.config.websocket.inbound;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public record StompMessageContext(Message<?> message, StompHeaderAccessor accessor) {

    public StompCommand command() {
        return accessor.getCommand();
    }
}
