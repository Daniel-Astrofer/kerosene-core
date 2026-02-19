package source.ledger.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BalanceEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public BalanceEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishBalanceUpdate(Long walletId, String walletName, Long userId,
            BigDecimal newBalance, BigDecimal amount, String context) {
        BalanceUpdateEvent event = new BalanceUpdateEvent(
                walletId, walletName, userId, newBalance, amount, context);

        // Publish to user-specific topic
        String destination = "/topic/balance/" + userId;
        messagingTemplate.convertAndSend(destination, event);

        System.out.println("📡 [WEBSOCKET] Published balance update to " + destination +
                " - Wallet: " + walletName + ", New Balance: " + newBalance);
    }
}
