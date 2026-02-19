package source.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketHandler;

import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Custom handshake handler to force STOMP protocol if the client doesn't send
        // it
        // This is CRITICAL because mobile clients often forget the sub-protocol header,
        // causing Spring to skip the STOMP protocol handler for that session.
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler() {
            @Override
            protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler webSocketHandler) {
                if (requestedProtocols.isEmpty()) {
                    System.out.println("⚠️ [WS-HANDSHAKE] No sub-protocol requested. Forcing v12.stomp");
                    return "v12.stomp";
                }
                return super.selectProtocol(requestedProtocols, webSocketHandler);
            }
        };

        // Main endpoint + SockJS
        registry.addEndpoint("/ws/balance")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();

        // Raw fallback
        registry.addEndpoint("/ws/raw-balance")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler);

        System.out.println("🔌 [WEBSOCKET] Endpoints registered (SockJS enabled with Forced STOMP)");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
            @Override
            public WebSocketHandler decorate(WebSocketHandler handler) {
                return new WebSocketHandlerDecorator(handler) {
                    @Override
                    public void handleMessage(WebSocketSession session,
                            org.springframework.web.socket.WebSocketMessage<?> message) throws Exception {
                        if (message instanceof TextMessage) {
                            String payload = ((TextMessage) message).getPayload();
                            String preview = payload.replace("\n", "\\n").replace("\r", "\\r");
                            if (preview.length() > 80)
                                preview = preview.substring(0, 80) + "...";
                            System.out.println("🕸️ [WS-RAW-IN] " + preview);
                        }
                        super.handleMessage(session, message);
                    }

                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                        System.out.println("✅ [WS-SESSION] Established: " + session.getId());
                        super.afterConnectionEstablished(session);
                    }
                };
            }
        });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    if (command != null) {
                        System.out.println("📥 [STOMP-IN] " + command + " | Session: " + accessor.getSessionId()
                                + " | User: " + accessor.getUser());
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    if (command != null) {
                        if (StompCommand.ERROR.equals(command)) {
                            System.err.println("❌ [STOMP-ERROR] " + accessor.getMessage());
                        } else if (StompCommand.CONNECTED.equals(command)) {
                            System.out.println("📤 [STOMP-OUT] CONNECTED | Session: " + accessor.getSessionId());
                        }
                    }
                }
                return message;
            }
        });
    }
}
