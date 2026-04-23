package source.ledger.application.transaction.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;
import source.ledger.application.transaction.TransactionNotificationPort;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;

import java.util.Map;

@Component
@Order(80)
public class TransactionNotificationHandler implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationHandler.class);

    private final TransactionNotificationPort notificationPort;

    public TransactionNotificationHandler(TransactionNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        try {
            notificationPort.notifyUser(
                    context.getReceiverWallet().getUser().getId(),
                    UserNotificationPayload.create(
                            NotificationKind.TRANSFER_RECEIVED,
                            NotificationSeverity.SUCCESS,
                            "Transferência Recebida",
                            String.format(
                                    "Aporte de %s BTC recebido de @%s para a carteira '%s'.",
                                    context.getTransaction().getAmount().toPlainString(),
                                    context.getSender().getUsername(),
                                    context.getReceiverWallet().getName()),
                            "/history",
                            "transaction",
                            context.getTransaction().getIdempotencyKey(),
                            Map.of(
                                    "walletName", context.getReceiverWallet().getName(),
                                    "amountBtc", context.getTransaction().getAmount().toPlainString(),
                                    "counterparty", context.getSender().getUsername())));

            notificationPort.notifyUser(
                    context.getSender().getId(),
                    UserNotificationPayload.create(
                            NotificationKind.TRANSFER_SENT,
                            NotificationSeverity.INFO,
                            "Transferência Enviada",
                            String.format(
                                    "Envio de %s BTC realizado para @%s a partir da carteira '%s'.",
                                    context.getTransaction().getAmount().toPlainString(),
                                    context.getReceiverWallet().getUser().getUsername(),
                                    context.getSenderWallet().getName()),
                            "/history",
                            "transaction",
                            context.getTransaction().getIdempotencyKey(),
                            Map.of(
                                    "walletName", context.getSenderWallet().getName(),
                                    "amountBtc", context.getTransaction().getAmount().toPlainString(),
                                    "counterparty", context.getReceiverWallet().getUser().getUsername())));
        } catch (Exception exception) {
            log.warn("Notification failed (non-blocking): {}", exception.getMessage());
        }

        chain.next(context);
    }
}
