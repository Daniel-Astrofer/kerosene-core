package source.config.websocket.inbound;

import java.util.List;
import java.util.Optional;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public class NativeHeaderStompTokenResolver extends AbstractStompTokenResolver {

    public NativeHeaderStompTokenResolver(StompTokenResolver next) {
        super(next);
    }

    @Override
    public Optional<String> resolve(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return resolveNext(accessor);
        }

        String token = stripBearerPrefix(authorizationHeaders.get(0));
        if (token.isBlank()) {
            return resolveNext(accessor);
        }
        return Optional.of(token);
    }

    private String stripBearerPrefix(String authorizationHeader) {
        if (authorizationHeader == null) {
            return "";
        }
        if (authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return authorizationHeader;
    }
}
