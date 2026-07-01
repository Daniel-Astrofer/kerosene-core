package source.notification.integration;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import source.common.financial.FinancialNotificationPort;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Component
@Primary
public class NotificationFinancialNotificationAdapter implements FinancialNotificationPort {

    private final NotificationService notificationService;

    public NotificationFinancialNotificationAdapter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notifyDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_CONFIRMED,
                        NotificationSeverity.SUCCESS,
                        depositMessageKey(rail),
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        Map.of(
                                "transactionId", transactionId.toString(),
                                "walletId", walletId.toString(),
                                "rail", rail,
                                "creditedSats", String.valueOf(creditedSats),
                                "confirmations", String.valueOf(confirmations)),
                        satsToBtc(creditedSats)));
    }

    @Override
    public void notifyPaymentRequestDepositConfirmed(
            Long userId,
            UUID transactionId,
            UUID paymentRequestId,
            String publicId,
            UUID walletId,
            String rail,
            long creditedSats) {
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_CONFIRMED,
                        NotificationSeverity.SUCCESS,
                        depositMessageKey(rail),
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        Map.of(
                                "transactionId", transactionId.toString(),
                                "paymentRequestId", paymentRequestId.toString(),
                                "publicId", publicId,
                                "walletId", walletId.toString(),
                                "creditedSats", String.valueOf(creditedSats),
                                "devInstantCredit", "true"),
                        satsToBtc(creditedSats)));
    }

    @Override
    public void notifyDemoBalanceCredited(Long userId, UUID walletId, String walletName, String amountBtc) {
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.TRANSFER_RECEIVED,
                        NotificationSeverity.SUCCESS,
                        NotificationMessageKey.DEMO_BALANCE_CREDITED,
                        "/home",
                        "wallet",
                        walletId.toString(),
                        Map.of(
                                "walletId", walletId.toString(),
                                "walletName", walletName,
                                "amountBtc", amountBtc),
                        amountBtc,
                        walletName));
    }

    private NotificationMessageKey depositMessageKey(String rail) {
        return "LIGHTNING".equalsIgnoreCase(rail)
                ? NotificationMessageKey.EXTERNAL_LIGHTNING_DEPOSIT_CONFIRMED
                : NotificationMessageKey.EXTERNAL_ONCHAIN_DEPOSIT_CONFIRMED;
    }

    private String satsToBtc(long sats) {
        return BigDecimal.valueOf(sats)
                .divide(BigDecimal.valueOf(100_000_000L), 8, RoundingMode.UNNECESSARY)
                .toPlainString();
    }
}
