package com.kerosene.platform.config.websocket.inbound;

import org.springframework.messaging.Message;

public interface StompMessageHandler {

    Message<?> handle(StompMessageContext context);
}
