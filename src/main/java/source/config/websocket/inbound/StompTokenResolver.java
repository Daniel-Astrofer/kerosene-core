package source.config.websocket.inbound;

import java.util.Optional;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public interface StompTokenResolver {

    Optional<String> resolve(StompHeaderAccessor accessor);
}
