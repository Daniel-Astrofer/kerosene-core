package source.config.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Component
public class LoggingWebSocketHandlerDecoratorFactory implements WebSocketHandlerDecoratorFactory {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebSocketHandlerDecoratorFactory.class);

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {

            @Override
            public void handleMessage(WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> message)
                    throws Exception {
                if (message instanceof TextMessage textMessage) {
                    String preview = sanitizePreview(textMessage.getPayload());
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

    private String sanitizePreview(String payload) {
        String preview = payload.replace("\n", "\\n").replace("\r", "\\r");
        if (preview.length() > 80) {
            return preview.substring(0, 80) + "...";
        }
        return preview;
    }
}
