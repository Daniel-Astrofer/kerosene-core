package source.config.websocket.inbound;

import java.util.Map;
import java.util.Optional;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public class SessionAttributeStompTokenResolver extends AbstractStompTokenResolver {

    public SessionAttributeStompTokenResolver(StompTokenResolver next) {
        super(next);
    }

    @Override
    public Optional<String> resolve(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return resolveNext(accessor);
        }

        Object token = sessionAttributes.get("token");
        if (token instanceof String tokenValue && !tokenValue.isBlank()) {
            return Optional.of(tokenValue);
        }
        return resolveNext(accessor);
    }
}
