package source.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import java.util.Map;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Autowired
    private JwtServicer jwtServicer;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setTaskScheduler(heartBeatScheduler()) // Required for heartbeats
                .setHeartbeatValue(new long[] { 10000, 10000 }); // 10 sec heartbeat (sends, expects)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        return new ThreadPoolTaskScheduler();
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
                    log.warn("[WS-HANDSHAKE] No sub-protocol requested. Forcing v12.stomp");
                    return "v12.stomp";
                }
                return super.selectProtocol(requestedProtocols, webSocketHandler);
            }
        };

        // Handshake Interceptor to extract token from Query Params
        HandshakeInterceptor tokenInterceptor = new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                if (request instanceof ServletServerHttpRequest) {
                    ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                    String token = servletRequest.getServletRequest().getParameter("token");
                    if (token != null) {
                        attributes.put("token", token);
                    }
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler wsHandler, Exception exception) {
            }
        };

        // Main endpoint + SockJS
        registry.addEndpoint("/ws/balance")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(tokenInterceptor)
                .withSockJS();

        // Raw fallback
        registry.addEndpoint("/ws/raw-balance")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(tokenInterceptor);

        // Payment request real-time notifications
        // Clients subscribe to: /topic/payment-request/{linkId}
        // Pushed when payRequest() marks the link as PAID
        registry.addEndpoint("/ws/payment-request")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(tokenInterceptor)
                .withSockJS();

        registry.addEndpoint("/ws/raw-payment-request")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(tokenInterceptor);

        log.info("[WEBSOCKET] Endpoints registered (SockJS enabled + Token Query Param supported)");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(20 * 1000) // Increase to 20 seconds
                .setSendBufferSizeLimit(512 * 1024); // Increase to 512KB

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
                            log.debug("[WS-RAW-IN] {}", preview);
                        }
                        super.handleMessage(session, message);
                    }

                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                        log.debug("[WS-SESSION] Established: {}", session.getId());
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
                if (accessor == null)
                    return message;

                StompCommand command = accessor.getCommand();

                if (StompCommand.CONNECT.equals(command)) {
                    String token = null;

                    // 1. Try from Native Headers (STOMP frames)
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    if (authorization != null && !authorization.isEmpty()) {
                        token = authorization.get(0).replace("Bearer ", "");
                    }

                    // 2. Fallback to Session Attributes (Handshake query param)
                    if (token == null && accessor.getSessionAttributes() != null) {
                        token = (String) accessor.getSessionAttributes().get("token");
                    }

                    if (token == null) {
                        // No token at all — reject immediately, do not let connection proceed.
                        // This prevents resource exhaustion from unauthenticated WebSocket sessions.
                        log.warn("[STOMP-AUTH] CONNECT rejected: no token in headers or query params. Session: {}",
                                accessor.getSessionId());
                        throw new MessageDeliveryException("Unauthorized: JWT token is required to connect.");
                    }

                    try {
                        Long userId = jwtServicer.extractId(token);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userId.toString(), null, Collections.emptyList());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        accessor.setUser(auth);
                        log.debug("[STOMP-AUTH] CONNECT authenticated for user: {}", userId);
                    } catch (Exception e) {
                        // Invalid or expired JWT — hard-reject the CONNECT frame.
                        // Previously this was swallowed silently, allowing unauthenticated sessions.
                        log.warn("[STOMP-AUTH] CONNECT rejected: invalid JWT. Session: {}. Reason: {}",
                                accessor.getSessionId(), e.getMessage());
                        throw new MessageDeliveryException(
                                "Unauthorized: Invalid or expired JWT token. Connection refused.");
                    }
                }

                // Enforce security on SUBSCRIBE — user must be set from CONNECT
                if (StompCommand.SUBSCRIBE.equals(command)) {
                    if (accessor.getUser() == null) {
                        log.warn("[STOMP-AUTH] Unauthorized SUBSCRIBE to: {}", accessor.getDestination());
                        throw new MessageDeliveryException("Unauthorized: Please connect with a valid JWT token");
                    }
                }

                if (command != null) {
                    log.debug("[STOMP-IN] {} | Session: {} | User: {}", command, accessor.getSessionId(),
                            accessor.getUser() != null ? accessor.getUser().getName() : "UNAUTHENTICATED");
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
                            log.warn("[STOMP-ERROR] {}", accessor.getMessage());
                        } else if (StompCommand.CONNECTED.equals(command)) {
                            log.debug("[STOMP-OUT] CONNECTED | Session: {}", accessor.getSessionId());
                        }
                    }
                }
                return message;
            }
        });
    }
}
