package source.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Component
public class WebSocketEventListener {

    @EventListener
    public void handleSessionConnectEvent(SessionConnectEvent event) {
        System.out.println("⚡ [WS-EVENT] SessionConnectEvent: " + event.getMessage());
    }

    @EventListener
    public void handleSessionConnectedEvent(SessionConnectedEvent event) {
        System.out.println("⚡ [WS-EVENT] SessionConnectedEvent: " + event.getMessage());
    }

    @EventListener
    public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
        System.out.println(
                "⚡ [WS-EVENT] SessionDisconnectEvent: " + event.getMessage() + ", Status: " + event.getCloseStatus());
    }

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        System.out.println("⚡ [WS-EVENT] SessionSubscribeEvent: " + event.getMessage());
    }

    @EventListener
    public void handleSessionUnsubscribeEvent(SessionUnsubscribeEvent event) {
        System.out.println("⚡ [WS-EVENT] SessionUnsubscribeEvent: " + event.getMessage());
    }
}
