package source.config.websocket;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class StompProtocolHandshakeHandler extends DefaultHandshakeHandler {

    private static final Logger log = LoggerFactory.getLogger(StompProtocolHandshakeHandler.class);

    @Override
    protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler webSocketHandler) {
        if (requestedProtocols.isEmpty()) {
            log.warn("[WS-HANDSHAKE] No sub-protocol requested. Forcing v12.stomp");
            return "v12.stomp";
        }
        return super.selectProtocol(requestedProtocols, webSocketHandler);
    }
}
