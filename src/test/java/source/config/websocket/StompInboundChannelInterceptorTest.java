package source.config.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.context.SecurityContextHolder;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.config.websocket.inbound.StompInboundMessageHandlerChain;

class StompInboundChannelInterceptorTest {

    @Mock
    private JwtServicer jwtServicer;

    private AutoCloseable mocks;
    private MessageChannel channel;
    private StompInboundChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        channel = mock(MessageChannel.class);
        interceptor = new StompInboundChannelInterceptor(new StompInboundMessageHandlerChain(jwtServicer));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void shouldAuthenticateConnectUsingAuthorizationHeader() {
        when(jwtServicer.extractId("header-token")).thenReturn(42L);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-header");
        accessor.setNativeHeader("Authorization", "Bearer header-token");

        Message<?> result = interceptor.preSend(message(accessor), channel);
        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

        assertNotNull(resultAccessor);
        assertNotNull(resultAccessor.getUser());
        assertEquals("42", resultAccessor.getUser().getName());
        verify(jwtServicer).extractId("header-token");
    }

    @Test
    void shouldAuthenticateConnectUsingHandshakeTokenFallback() {
        when(jwtServicer.extractId("handshake-token")).thenReturn(7L);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-fallback");
        accessor.setSessionAttributes(new HashMap<>(Map.of("token", "handshake-token")));

        Message<?> result = interceptor.preSend(message(accessor), channel);
        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

        assertNotNull(resultAccessor);
        assertNotNull(resultAccessor.getUser());
        assertEquals("7", resultAccessor.getUser().getName());
        verify(jwtServicer).extractId("handshake-token");
    }

    @Test
    void shouldRejectConnectWithoutAnyToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-missing-token");

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void shouldRejectSubscribeWithoutAuthenticatedUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/payment-request/123");

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
