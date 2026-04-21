package source.config.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Component
public class WebSocketTransportCustomizer {

    private final LoggingWebSocketHandlerDecoratorFactory loggingDecoratorFactory;

    public WebSocketTransportCustomizer(LoggingWebSocketHandlerDecoratorFactory loggingDecoratorFactory) {
        this.loggingDecoratorFactory = loggingDecoratorFactory;
    }

    public void configure(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(20 * 1000)
                .setSendBufferSizeLimit(512 * 1024);
        registration.addDecoratorFactory(loggingDecoratorFactory);
    }
}
