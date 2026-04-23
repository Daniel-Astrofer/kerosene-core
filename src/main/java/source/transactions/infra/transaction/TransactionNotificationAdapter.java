package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;
import source.transactions.application.transaction.TransactionNotificationPort;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
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

        String body = "A transação foi enviada para processamento na rede Blockchain.";
        if (amount != null) {
            body = String.format("O envio de %s BTC foi transmitido com sucesso.", amount.toPlainString());
        }

        notificationService.notifyUser(
                userId,
                NotificationKind.PAYMENT_SENT,
                NotificationSeverity.INFO,
                "Transação Transmitida",
                body,
                "/history",
                "transaction",
                null,
                amount == null ? Map.of() : Map.of("amountBtc", amount.toPlainString()));
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

        String body = "Uma nova transferência foi identificada em sua carteira.";
        if (amount != null) {
            body = String.format("Aporte de %s BTC identificado na carteira '%s'.",
                    amount.toPlainString(),
                    wallet.getName());
        }
        if (message != null && !message.isBlank()) {
            body += " Mensagem: " + message;
        }

        notificationService.notifyUser(
                wallet.getUser().getId(),
                NotificationKind.DEPOSIT_DETECTED,
                NotificationSeverity.INFO,
                "Recurso Recebido",
                body,
                "/deposits",
                "wallet",
                wallet.getId() != null ? wallet.getId().toString() : null,
                amount == null
                        ? Map.of("walletName", wallet.getName())
                        : Map.of(
                                "walletName", wallet.getName(),
                                "amountBtc", amount.toPlainString()));
    }
}
