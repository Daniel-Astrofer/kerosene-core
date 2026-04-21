package source.config.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@Component
public class WebSocketEndpointRegistrar {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEndpointRegistrar.class);

    private final StompProtocolHandshakeHandler handshakeHandler;
    private final QueryParamTokenHandshakeInterceptor tokenHandshakeInterceptor;

    public WebSocketEndpointRegistrar(
            StompProtocolHandshakeHandler handshakeHandler,
            QueryParamTokenHandshakeInterceptor tokenHandshakeInterceptor) {
        this.handshakeHandler = handshakeHandler;
        this.tokenHandshakeInterceptor = tokenHandshakeInterceptor;
    }

    public void register(StompEndpointRegistry registry) {
        registerEndpoint(registry, "/ws/balance", true);
        registerEndpoint(registry, "/ws/raw-balance", false);
        registerEndpoint(registry, "/ws/payment-request", true);
        registerEndpoint(registry, "/ws/raw-payment-request", false);

        log.info("[WEBSOCKET] Endpoints registered (SockJS enabled + Token Query Param supported)");
    }

    private void registerEndpoint(StompEndpointRegistry registry, String path, boolean sockJsEnabled) {
        var endpointRegistration = registry.addEndpoint(path)
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(tokenHandshakeInterceptor);

        if (sockJsEnabled) {
            endpointRegistration.withSockJS();
        }
    }
}
