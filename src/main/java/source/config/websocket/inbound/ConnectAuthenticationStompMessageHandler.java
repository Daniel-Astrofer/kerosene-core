package source.config.websocket.inbound;

import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;

public class ConnectAuthenticationStompMessageHandler extends AbstractStompMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnectAuthenticationStompMessageHandler.class);

    private final JwtServicer jwtServicer;
    private final StompTokenResolver tokenResolver;

    public ConnectAuthenticationStompMessageHandler(
            JwtServicer jwtServicer,
            StompTokenResolver tokenResolver,
            StompMessageHandler next) {
        super(next);
        this.jwtServicer = jwtServicer;
        this.tokenResolver = tokenResolver;
    }

    @Override
    public Message<?> handle(StompMessageContext context) {
        if (!StompCommand.CONNECT.equals(context.command())) {
            return passToNext(context);
        }

        String token = tokenResolver.resolve(context.accessor())
                .orElseThrow(() -> {
                    log.warn("[STOMP-AUTH] CONNECT rejected: no token in headers or query params. Session: {}",
                            context.accessor().getSessionId());
                    return new MessageDeliveryException("Unauthorized: JWT token is required to connect.");
                });

        try {
            Long userId = jwtServicer.extractId(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId.toString(),
                    null,
                    Collections.emptyList());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            context.accessor().setUser(authentication);
            log.debug("[STOMP-AUTH] CONNECT authenticated for user: {}", userId);
        } catch (Exception exception) {
            log.warn("[STOMP-AUTH] CONNECT rejected: invalid JWT. Session: {}. Reason: {}",
                    context.accessor().getSessionId(),
                    exception.getMessage());
            throw new MessageDeliveryException(
                    "Unauthorized: Invalid or expired JWT token. Connection refused.");
        }

        return passToNext(context);
    }
}
