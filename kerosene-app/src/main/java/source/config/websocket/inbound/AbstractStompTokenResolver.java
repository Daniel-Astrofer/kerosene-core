package source.config.websocket.inbound;

import java.util.Optional;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public abstract class AbstractStompTokenResolver implements StompTokenResolver {

    private final StompTokenResolver next;

    protected AbstractStompTokenResolver(StompTokenResolver next) {
        this.next = next;
    }

    protected Optional<String> resolveNext(StompHeaderAccessor accessor) {
        if (next == null) {
            return Optional.empty();
        }
        return next.resolve(accessor);
    }
}
