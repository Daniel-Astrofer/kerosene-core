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
                                "creditedSats", String.valueOf(creditedSats)),
                        satsToBtc(creditedSats)));
    }

    @Override
    public void notifyDepositDetected(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_DETECTED,
                        NotificationSeverity.WARNING,
                        NotificationMessageKey.PENDING_DEPOSIT_DETECTED,
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        Map.of(
                                "transactionId", transactionId.toString(),
                                "walletId", walletId.toString(),
                                "rail", rail == null ? "ONCHAIN" : rail,
                                "creditedSats", String.valueOf(creditedSats),
                                "confirmations", String.valueOf(confirmations),
                                "direction", "INBOUND"),
                        satsToBtc(creditedSats)));
    }

    @Override
    public void notifyDepositConfirmationProgress(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            int confirmations) {
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_DETECTED,
                        NotificationSeverity.WARNING,
                        NotificationMessageKey.PENDING_DEPOSIT_PROGRESS,
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        Map.of(
                                "transactionId", transactionId.toString(),
                                "walletId", walletId.toString(),
                                "rail", rail,
                                "creditedSats", String.valueOf(creditedSats),
                                "confirmations", String.valueOf(confirmations)),
                        satsToBtc(creditedSats),
                        String.valueOf(confirmations)));
    }

    @Override
    public void notifyOutboundDetected(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations,
            String destinationHint) {
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.WARNING,
                        NotificationMessageKey.COLD_OUTBOUND_DETECTED,
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        Map.of(
                                "transactionId", transactionId.toString(),
                                "walletId", walletId.toString(),
                                "rail", rail == null ? "ONCHAIN" : rail,
                                "amountSats", String.valueOf(amountSats),
                                "confirmations", String.valueOf(confirmations),
                                "destination", destinationHint == null ? "" : destinationHint,
                                "direction", "OUTBOUND"),
                        satsToBtc(amountSats)));
    }

    @Override
    public void notifyOutboundConfirmed(
            Long userId,
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations) {
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.SUCCESS,
                        NotificationMessageKey.COLD_OUTBOUND_CONFIRMED,
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        Map.of(
                                "transactionId", transactionId.toString(),
                                "walletId", walletId.toString(),
                                "rail", rail == null ? "ONCHAIN" : rail,
                                "amountSats", String.valueOf(amountSats),
                                "confirmations", String.valueOf(confirmations),
                                "direction", "OUTBOUND"),
                        satsToBtc(amountSats)));
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
