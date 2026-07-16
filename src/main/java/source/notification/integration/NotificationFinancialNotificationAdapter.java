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
import java.util.LinkedHashMap;
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
                        creditMeta(transactionId, walletId, rail, creditedSats, confirmations, "INBOUND"),
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
        Map<String, String> meta = creditMeta(transactionId, walletId, rail, creditedSats, null, "INBOUND");
        meta.put("paymentRequestId", paymentRequestId.toString());
        if (publicId != null) {
            meta.put("publicId", publicId);
        }
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.DEPOSIT_CONFIRMED,
                        NotificationSeverity.SUCCESS,
                        depositMessageKey(rail),
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        meta,
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
                        creditMeta(
                                transactionId,
                                walletId,
                                rail == null ? "ONCHAIN" : rail,
                                creditedSats,
                                confirmations,
                                "INBOUND"),
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
                        creditMeta(transactionId, walletId, rail, creditedSats, confirmations, "INBOUND"),
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
        Map<String, String> meta = debitMeta(
                transactionId,
                walletId,
                rail == null ? "ONCHAIN" : rail,
                amountSats,
                confirmations);
        if (destinationHint != null && !destinationHint.isBlank()) {
            meta.put("destination", destinationHint);
        }
        notificationService.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.WARNING,
                        NotificationMessageKey.COLD_OUTBOUND_DETECTED,
                        "/home",
                        "transaction",
                        transactionId.toString(),
                        meta,
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
                        debitMeta(
                                transactionId,
                                walletId,
                                rail == null ? "ONCHAIN" : rail,
                                amountSats,
                                confirmations),
                        satsToBtc(amountSats)));
    }

    private NotificationMessageKey depositMessageKey(String rail) {
        return "LIGHTNING".equalsIgnoreCase(rail)
                ? NotificationMessageKey.EXTERNAL_LIGHTNING_DEPOSIT_CONFIRMED
                : NotificationMessageKey.EXTERNAL_ONCHAIN_DEPOSIT_CONFIRMED;
    }

    private Map<String, String> creditMeta(
            UUID transactionId,
            UUID walletId,
            String rail,
            long creditedSats,
            Integer confirmations,
            String direction) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("transactionId", transactionId.toString());
        meta.put("walletId", walletId.toString());
        meta.put("rail", rail == null ? "ONCHAIN" : rail);
        meta.put("creditedSats", String.valueOf(creditedSats));
        meta.put("amountSats", String.valueOf(creditedSats));
        meta.put("amountBtc", satsToBtc(creditedSats));
        if (confirmations != null) {
            meta.put("confirmations", String.valueOf(confirmations));
        }
        if (direction != null) {
            meta.put("direction", direction);
        }
        return meta;
    }

    private Map<String, String> debitMeta(
            UUID transactionId,
            UUID walletId,
            String rail,
            long amountSats,
            int confirmations) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("transactionId", transactionId.toString());
        meta.put("walletId", walletId.toString());
        meta.put("rail", rail);
        meta.put("amountSats", String.valueOf(amountSats));
        meta.put("amountBtc", satsToBtc(amountSats));
        meta.put("confirmations", String.valueOf(confirmations));
        meta.put("direction", "OUTBOUND");
        return meta;
    }

    private String satsToBtc(long sats) {
        return BigDecimal.valueOf(sats)
                .divide(BigDecimal.valueOf(100_000_000L), 8, RoundingMode.UNNECESSARY)
                .stripTrailingZeros()
                .toPlainString();
    }
}
