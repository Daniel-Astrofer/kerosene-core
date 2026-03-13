package source.ledger.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import source.ledger.dto.InternalPaymentRequestDTO;

/**
 * Publishes WebSocket events related to internal payment request lifecycle.
 *
 * Subscribers:
 * - Creator listens on /topic/payment-request/{linkId}
 * and receives a push the moment a payer settles the request.
 */
@Service
public class PaymentRequestEventPublisher {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentRequestEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public PaymentRequestEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Fired when a payment request transitions to PAID.
     * The creator's client subscribes to /topic/payment-request/{linkId}
     * and receives the full updated DTO.
     *
     * @param req the settled payment request DTO (status=PAID, paidAt filled)
     */
    public void publishPaymentPaid(InternalPaymentRequestDTO req) {
        String destination = "/topic/payment-request/" + req.getId();
        messagingTemplate.convertAndSend(destination, req);
        log.info("[WS] Pushed PAID event to {} | amount={} BTC", destination, req.getAmount());
    }

    /**
     * Fired when a payment request expires before being paid.
     * Allows the UI to react immediately without polling.
     *
     * @param req the expired payment request DTO
     */
    public void publishPaymentExpired(InternalPaymentRequestDTO req) {
        String destination = "/topic/payment-request/" + req.getId();
        messagingTemplate.convertAndSend(destination, req);
        log.info("[WS] Pushed EXPIRED event to {}", destination);
    }
}
