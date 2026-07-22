package source.config.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import source.config.websocket.inbound.StompInboundMessageHandlerChain;
import source.config.websocket.inbound.StompMessageContext;

@Component
public class StompInboundChannelInterceptor implements ChannelInterceptor {

    private final StompInboundMessageHandlerChain handlerChain;

    public StompInboundChannelInterceptor(StompInboundMessageHandlerChain handlerChain) {
        this.handlerChain = handlerChain;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        return handlerChain.handle(new StompMessageContext(message, accessor));
    }
}
