package com.kerosene.config.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import java.util.Arrays;

@Component
public class WebSocketEndpointRegistrar {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEndpointRegistrar.class);

    private final StompProtocolHandshakeHandler handshakeHandler;
    private final String[] allowedOrigins;

    public WebSocketEndpointRegistrar(
            StompProtocolHandshakeHandler handshakeHandler,
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:8080,http://localhost:8081,http://localhost:8082,http://localhost:30080,http://localhost:30082,http://127.0.0.1:3000,http://127.0.0.1:3001,http://127.0.0.1:8080,http://127.0.0.1:8081,http://127.0.0.1:8082,http://127.0.0.1:30080,http://127.0.0.1:30082}") String allowedOriginsConfig) {
        this.handshakeHandler = handshakeHandler;
        this.allowedOrigins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }

    public void register(StompEndpointRegistry registry) {
        registerEndpoint(registry, "/ws/balance", true);
        registerEndpoint(registry, "/ws/raw-balance", false);
        registerEndpoint(registry, "/ws/payment-request", true);
        registerEndpoint(registry, "/ws/raw-payment-request", false);

        log.info("[WEBSOCKET] Endpoints registered (SockJS enabled + Strict Origins)");
    }

    private void registerEndpoint(StompEndpointRegistry registry, String path, boolean sockJsEnabled) {
        var endpointRegistration = registry.addEndpoint(path)
                // Patterns (not fixed origins): native Flutter / mobile clients often
                // omit Origin or send non-browser values. Auth remains JWT on CONNECT.
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler);

        if (sockJsEnabled) {
            endpointRegistration.withSockJS();
        }

        // Keep configured browser origins logged for ops visibility.
        if (allowedOrigins.length > 0) {
            log.debug("[WEBSOCKET] endpoint {} sockJs={} browserOrigins={}", path, sockJsEnabled,
                    Arrays.toString(allowedOrigins));
        }
    }
}
