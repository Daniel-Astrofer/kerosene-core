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

        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId),
                "/queue/balance",
                event);
        log.info("[WS] Published balance update to user {} /queue/balance - Wallet: {}, NewBalance: {}",
                userId,
                walletName,
                newBalance);
    }
}
