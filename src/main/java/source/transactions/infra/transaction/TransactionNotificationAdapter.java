package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;
import source.transactions.application.transaction.TransactionNotificationPort;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TransactionNotificationAdapter implements TransactionNotificationPort {

    private final NotificationService notificationService;
    private final WalletRepository walletRepository;

    public TransactionNotificationAdapter(
            NotificationService notificationService,
            WalletRepository walletRepository) {
        this.notificationService = notificationService;
        this.walletRepository = walletRepository;
    }

    @Override
    public void notifySenderBroadcast(Long userId, BigDecimal amount) {
        if (userId == null) {
            return;
        }

        NotificationMessageKey messageKey = amount == null
                ? NotificationMessageKey.TRANSACTION_BROADCAST_NO_AMOUNT
                : NotificationMessageKey.TRANSACTION_BROADCAST_WITH_AMOUNT;

        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.INFO,
                        messageKey,
                        "/history",
                        "transaction",
                        null,
                        amount == null ? Map.of() : Map.of("amountBtc", amount.toPlainString()),
                        amount != null ? amount.toPlainString() : null));
    }

    @Override
    public void notifyRecipientBroadcast(String address, BigDecimal amount, String message) {
        if (address == null || address.isBlank()) {
            return;
        }

        WalletEntity wallet = walletRepository.findByPassphraseHash(address);
        if (wallet == null || wallet.getUser() == null) {
            return;
        }

        boolean hasAmount = amount != null;
        boolean hasMessage = message != null && !message.isBlank();
        NotificationMessageKey messageKey = NotificationMessageKey.WALLET_ENTRY_DETECTED;
        if (hasAmount && hasMessage) {
            messageKey = NotificationMessageKey.WALLET_ENTRY_AMOUNT_MESSAGE_DETECTED;
        } else if (hasAmount) {
            messageKey = NotificationMessageKey.WALLET_ENTRY_AMOUNT_DETECTED;
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("walletName", wallet.getName());
        if (hasAmount) {
            metadata.put("amountBtc", amount.toPlainString());
        }
        if (hasMessage) {
            metadata.put("message", message.trim());
        }

        notificationService.notifyUser(
                wallet.getUser().getId(),
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_DETECTED,
                        NotificationSeverity.INFO,
                        messageKey,
                        "/deposits",
                        "wallet",
                        wallet.getId() != null ? wallet.getId().toString() : null,
                        metadata,
                        hasAmount ? amount.toPlainString() : null,
                        hasMessage ? message.trim() : wallet.getName()));
    }
}
