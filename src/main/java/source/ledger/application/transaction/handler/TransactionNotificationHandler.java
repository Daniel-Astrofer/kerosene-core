package source.ledger.application.transaction.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;
import source.ledger.application.transaction.TransactionNotificationPort;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;

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
                    NotificationMessages.payload(
                            NotificationKind.TRANSFER_RECEIVED,
                            NotificationSeverity.SUCCESS,
                            NotificationMessageKey.INTERNAL_TRANSFER_RECEIVED,
                            "/history",
                            "transaction",
                            context.getTransaction().getIdempotencyKey(),
                            Map.of(
                                    "walletName", context.getReceiverWallet().getName(),
                                    "amountBtc", context.getTransaction().getAmount().toPlainString(),
                                    "counterparty", context.getSender().getUsername()),
                            context.getTransaction().getAmount().toPlainString(),
                            context.getReceiverWallet().getName()));

            notificationPort.notifyUser(
                    context.getSender().getId(),
                    NotificationMessages.payload(
                            NotificationKind.TRANSFER_SENT,
                            NotificationSeverity.INFO,
                            NotificationMessageKey.INTERNAL_TRANSFER_SENT,
                            "/history",
                            "transaction",
                            context.getTransaction().getIdempotencyKey(),
                            Map.of(
                                    "walletName", context.getSenderWallet().getName(),
                                    "amountBtc", context.getTransaction().getAmount().toPlainString(),
                                    "counterparty", context.getReceiverWallet().getUser().getUsername()),
                            context.getTransaction().getAmount().toPlainString(),
                            context.getSenderWallet().getName()));
        } catch (Exception exception) {
            log.warn("Notification failed (non-blocking): {}", exception.getMessage());
        }

        chain.next(context);
    }
}
