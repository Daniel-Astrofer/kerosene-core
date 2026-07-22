package source.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import source.config.websocket.StompInboundChannelInterceptor;
import source.config.websocket.StompOutboundChannelInterceptor;
import source.config.websocket.WebSocketEndpointRegistrar;
import source.config.websocket.WebSocketTransportCustomizer;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TaskScheduler heartBeatScheduler;
    private final WebSocketEndpointRegistrar endpointRegistrar;
    private final WebSocketTransportCustomizer transportCustomizer;
    private final StompInboundChannelInterceptor inboundChannelInterceptor;
    private final StompOutboundChannelInterceptor outboundChannelInterceptor;

    public WebSocketConfig(
            @Qualifier("heartBeatScheduler") TaskScheduler heartBeatScheduler,
            WebSocketEndpointRegistrar endpointRegistrar,
            WebSocketTransportCustomizer transportCustomizer,
            StompInboundChannelInterceptor inboundChannelInterceptor,
            StompOutboundChannelInterceptor outboundChannelInterceptor) {
        this.heartBeatScheduler = heartBeatScheduler;
        this.endpointRegistrar = endpointRegistrar;
        this.transportCustomizer = transportCustomizer;
        this.inboundChannelInterceptor = inboundChannelInterceptor;
        this.outboundChannelInterceptor = outboundChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(heartBeatScheduler)
                .setHeartbeatValue(new long[] { 10000, 10000 });
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        endpointRegistrar.register(registry);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        transportCustomizer.configure(registration);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(outboundChannelInterceptor);
    }
}
