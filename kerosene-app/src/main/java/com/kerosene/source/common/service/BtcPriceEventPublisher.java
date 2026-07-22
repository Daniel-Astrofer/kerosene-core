package source.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Broadcasts BTC market quotes to all authenticated STOMP sessions.
 *
 * <p>Destination: {@code /topic/btc-price}. Payload matches
 * {@code GET /api/economy/btc-price} data map.
 */
@Service
public class BtcPriceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BtcPriceEventPublisher.class);

    public static final String DESTINATION = "/topic/btc-price";

    private final SimpMessagingTemplate messagingTemplate;

    public BtcPriceEventPublisher(ObjectProvider<SimpMessagingTemplate> messagingTemplate) {
        this.messagingTemplate = messagingTemplate.getIfAvailable();
    }

    public void publish(Map<String, Object> quote) {
        if (messagingTemplate == null || quote == null || quote.isEmpty()) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(DESTINATION, quote);
            log.info(
                    "[WS] Published BTC price to {} usd={} brl={}",
                    DESTINATION,
                    quote.get("btcUsd"),
                    quote.get("btcBrl"));
        } catch (Exception e) {
            log.error("Failed to publish BTC price websocket event", e);
        }
    }
}
