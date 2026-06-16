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

        authorizeDestination(context);
        return passToNext(context);
    }

    private void authorizeDestination(StompMessageContext context) {
        String destination = context.accessor().getDestination();
        if (destination == null || destination.isBlank()) {
            return;
        }

        Long principalUserId = principalUserId(context);
        if (principalUserId == null) {
            reject(destination, "invalid principal");
        }

        if (destination.startsWith("/user/queue/")) {
            return;
        }

        if (destination.startsWith("/topic/balance/")) {
            Long destinationUserId = parseTrailingLong(destination, "/topic/balance/");
            if (destinationUserId == null || !destinationUserId.equals(principalUserId)) {
                reject(destination, "balance owner mismatch");
            }
            return;
        }

        if (destination.startsWith("/topic/payment-request/")) {
            reject(destination, "legacy payment request channel disabled");
        }
    }

    private Long principalUserId(StompMessageContext context) {
        java.security.Principal user = context.accessor().getUser();
        if (user == null) {
            return null;
        }
        try {
            return Long.valueOf(user.getName());
        } catch (Exception exception) {
            return null;
        }
    }

    private Long parseTrailingLong(String destination, String prefix) {
        try {
            String value = destination.substring(prefix.length()).trim();
            if (value.isBlank() || value.contains("/")) {
                return null;
            }
            return Long.valueOf(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private void reject(String destination, String reason) {
        log.warn("[STOMP-AUTH] Forbidden SUBSCRIBE to: {} ({})", destination, reason);
        throw new MessageDeliveryException("Forbidden: subscription destination is not owned by this user");
    }
}
