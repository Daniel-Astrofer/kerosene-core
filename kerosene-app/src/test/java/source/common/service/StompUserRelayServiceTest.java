package source.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompUserRelayServiceTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final StompUserRelayService service = new StompUserRelayService(messagingTemplate);

    @Test
    void publishesAllowlistedDestination() {
        service.publishToUser(7L, "/queue/balance", Map.of("walletId", "w1"));
        verify(messagingTemplate).convertAndSendToUser("7", "/queue/balance", Map.of("walletId", "w1"));
    }

    @Test
    void normalizesUserPrefixedDestination() {
        assertEquals("/queue/transactions", StompUserRelayService.normalizeDestination("/user/queue/transactions"));
        service.publishToUser(7L, "/user/queue/transactions", Map.of("id", "t"));
        verify(messagingTemplate).convertAndSendToUser("7", "/queue/transactions", Map.of("id", "t"));
    }

    @Test
    void rejectsUnknownDestination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.publishToUser(7L, "/queue/hack", Map.of("x", 1)));
    }
}
