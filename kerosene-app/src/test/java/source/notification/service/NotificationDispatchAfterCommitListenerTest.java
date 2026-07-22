package source.notification.service;

import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchAfterCommitListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PushNotificationPort pushNotificationPort;

    @InjectMocks
    private NotificationDispatchAfterCommitListener listener;

    @Test
    void shouldDispatchPersistedNotificationToUserQueueAndPushPort() {
        Map<String, Object> payload = Map.of(
                "id", 55L,
                "kind", "system.info",
                "title", "Novo evento");

        listener.handle(new NotificationPersistedEvent(11L, payload));

        verify(messagingTemplate).convertAndSendToUser("11", "/queue/notifications", payload);
        verify(pushNotificationPort).dispatch(11L, payload);
    }
}
