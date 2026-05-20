package source.ledger.application.paymentrequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.event.PaymentRequestEventPublisher;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;

import java.util.Map;

@Service
public class PaymentRequestNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestNotificationService.class);

    private final NotificationService notificationService;
    private final PaymentRequestEventPublisher paymentRequestEventPublisher;

    public PaymentRequestNotificationService(
            NotificationService notificationService,
            PaymentRequestEventPublisher paymentRequestEventPublisher) {
        this.notificationService = notificationService;
        this.paymentRequestEventPublisher = paymentRequestEventPublisher;
    }

    public void notifyCreated(InternalPaymentRequestDTO request) {
        try {
            notificationService.notifyUser(
                    request.getRequesterUserId(),
                    NotificationMessages.payload(
                            NotificationKind.PAYMENT_REQUEST_CREATED,
                            NotificationSeverity.INFO,
                            NotificationMessageKey.PAYMENT_REQUEST_CREATED,
                            "/history",
                            "payment_request",
                            request.getId() != null ? request.getId().toString() : null,
                            Map.of(
                                    "walletName", request.getReceiverWalletName(),
                                    "amountBtc", request.getAmount().toPlainString()),
                            request.getAmount().toPlainString(),
                            request.getReceiverWalletName()));
        } catch (Exception exception) {
            log.warn("Payment request creation notification failed (non-blocking): {}", exception.getMessage());
        }
    }

    public void notifyPaid(InternalPaymentRequestDTO request) {
        try {
            notificationService.notifyUser(
                    request.getRequesterUserId(),
                    NotificationMessages.payload(
                            NotificationKind.PAYMENT_REQUEST_PAID,
                            NotificationSeverity.SUCCESS,
                            NotificationMessageKey.PAYMENT_REQUEST_PAID,
                            "/history",
                            "payment_request",
                            request.getId() != null ? request.getId().toString() : null,
                            Map.of("amountBtc", request.getAmount().toPlainString()),
                            request.getAmount().toPlainString()));
        } catch (Exception exception) {
            log.warn("payRequest notification failed (non-blocking): {}", exception.getMessage());
        }

        try {
            paymentRequestEventPublisher.publishPaymentPaid(request);
        } catch (Exception exception) {
            log.warn("[WS-PAYMENT] Failed to push paid event for linkId={}: {}", request.getId(),
                    exception.getMessage());
        }
    }
}
