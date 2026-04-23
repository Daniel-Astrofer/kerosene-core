package source.ledger.application.paymentrequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.event.PaymentRequestEventPublisher;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;
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
                    UserNotificationPayload.create(
                            NotificationKind.PAYMENT_REQUEST_CREATED,
                            NotificationSeverity.INFO,
                            "Solicitação de Pagamento Gerada",
                            String.format(
                                    "Um novo link de pagamento no valor de %s BTC foi criado para a carteira '%s'.",
                                    request.getAmount().toPlainString(),
                                    request.getReceiverWalletName()),
                            "/history",
                            "payment_request",
                            request.getId() != null ? request.getId().toString() : null,
                            Map.of(
                                    "walletName", request.getReceiverWalletName(),
                                    "amountBtc", request.getAmount().toPlainString())));
        } catch (Exception exception) {
            log.warn("Payment request creation notification failed (non-blocking): {}", exception.getMessage());
        }
    }

    public void notifyPaid(InternalPaymentRequestDTO request) {
        try {
            notificationService.notifyUser(
                    request.getRequesterUserId(),
                    UserNotificationPayload.create(
                            NotificationKind.PAYMENT_REQUEST_PAID,
                            NotificationSeverity.SUCCESS,
                            "Solicitação de Pagamento Liquidada",
                            String.format(
                                    "Seu pedido de pagamento no valor de %s BTC foi processado com sucesso.",
                                    request.getAmount().toPlainString()),
                            "/history",
                            "payment_request",
                            request.getId() != null ? request.getId().toString() : null,
                            Map.of("amountBtc", request.getAmount().toPlainString())));
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
