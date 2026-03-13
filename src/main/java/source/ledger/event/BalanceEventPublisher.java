package source.ledger.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BalanceEventPublisher {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BalanceEventPublisher.class);

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
        log.info("[WS] Published balance update to {} - Wallet: {}, NewBalance: {}", destination, walletName,
                newBalance);
    }
}
