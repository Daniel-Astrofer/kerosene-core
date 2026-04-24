package source.transactions.application.paymentlink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.CreatePaymentLinkRequest;
import source.transactions.dto.PaymentLinkDTO;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentLinkCreator {

    private final PaymentLinkStore paymentLinkStore;
    private final PaymentLinkHistoryPort paymentLinkHistoryPort;
    private final PaymentLinkWalletPort paymentLinkWalletPort;
    private final PaymentLinkAddressAllocationPort addressAllocationPort;
    private final String serverDepositAddress;
    private final long paymentLinkExpirationMinutes;
    private final long maxCustomExpirationMinutes;

    public PaymentLinkCreator(
            PaymentLinkStore paymentLinkStore,
            PaymentLinkHistoryPort paymentLinkHistoryPort,
            PaymentLinkWalletPort paymentLinkWalletPort,
            PaymentLinkAddressAllocationPort addressAllocationPort,
            @Value("${bitcoin.deposit-address:1A1z7agoat7F9gq5TF...}") String serverDepositAddress,
            @Value("${bitcoin.payment-link-expiration-minutes:60}") long paymentLinkExpirationMinutes,
            @Value("${bitcoin.payment-link-max-expiration-minutes:10080}") long maxCustomExpirationMinutes) {
        this.paymentLinkStore = paymentLinkStore;
        this.paymentLinkHistoryPort = paymentLinkHistoryPort;
        this.paymentLinkWalletPort = paymentLinkWalletPort;
        this.addressAllocationPort = addressAllocationPort;
        this.serverDepositAddress = serverDepositAddress;
        this.paymentLinkExpirationMinutes = paymentLinkExpirationMinutes;
        this.maxCustomExpirationMinutes = maxCustomExpirationMinutes;
    }

    @Transactional
    public PaymentLinkDTO createForUser(Long userId, CreatePaymentLinkRequest request) {
        WalletEntity wallet = paymentLinkWalletPort.findPrimaryWallet(userId);
        if (wallet == null) {
            throw new IllegalStateException("User has no wallet configured to receive the payment link credit.");
        }

        PaymentLinkDTO paymentLink = newPaymentLink(
                request.getAmount(),
                request.getDescription(),
                request.getExpiresInMinutes(),
                request.getVisibility(),
                request.getConfirmationMode(),
                request.getAmountLocked(),
                request.getReferenceLabel(),
                request.getMetadata());
        PaymentLinkAddressAllocationPort.Allocation allocation = addressAllocationPort.allocate(
                userId,
                wallet,
                "payment-link:" + paymentLink.getId(),
                true);
        paymentLink.setUserId(userId);
        paymentLink.setDepositAddress(allocation.address());
        paymentLinkStore.save(paymentLink);
        paymentLinkHistoryPort.recordCreated(paymentLink);
        return paymentLink;
    }

    @Transactional
    public PaymentLinkDTO createForAccountActivation(Long userId, BigDecimal amountBtc) {
        PaymentLinkDTO paymentLink = newPaymentLink(
                amountBtc,
                PaymentLinkDescription.ACCOUNT_ACTIVATION,
                null,
                PaymentLinkVisibility.PRIVATE,
                PaymentLinkConfirmationMode.MANUAL_REVIEW,
                true,
                "ACTIVATION",
                Map.of("flow", "activation"));
        paymentLink.setUserId(userId);
        paymentLinkStore.save(paymentLink);
        paymentLinkHistoryPort.recordCreated(paymentLink);
        return paymentLink;
    }

    public PaymentLinkDTO createForOnboarding(String sessionId, BigDecimal amountBtc, String description) {
        PaymentLinkDTO paymentLink = newPaymentLink(
                amountBtc,
                description,
                null,
                PaymentLinkVisibility.PRIVATE,
                PaymentLinkConfirmationMode.MANUAL_REVIEW,
                true,
                "ONBOARDING",
                Map.of("flow", "onboarding"));
        paymentLink.setSessionId(sessionId);
        paymentLinkStore.save(paymentLink);
        return paymentLink;
    }

    private PaymentLinkDTO newPaymentLink(
            BigDecimal amountBtc,
            String description,
            Integer expiresInMinutes,
            String visibility,
            String confirmationMode,
            Boolean amountLocked,
            String referenceLabel,
            Map<String, String> metadata) {
        validateAmount(amountBtc);
        LocalDateTime now = LocalDateTime.now();
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId(generatePaymentLinkId());
        paymentLink.setAmountBtc(amountBtc);
        paymentLink.setGrossAmountBtc(amountBtc);
        paymentLink.setDescription(description);
        paymentLink.setDepositAddress(serverDepositAddress);
        paymentLink.setVisibility(resolveVisibility(visibility));
        paymentLink.setConfirmationMode(resolveConfirmationMode(confirmationMode));
        paymentLink.setAmountLocked(amountLocked == null || amountLocked);
        paymentLink.setReferenceLabel(normalizeReferenceLabel(referenceLabel));
        paymentLink.setMetadata(sanitizeMetadata(metadata));
        paymentLink.setStatus(PaymentLinkStatus.PENDING);
        paymentLink.setCreatedAt(now);
        paymentLink.setExpiresAt(now.plusMinutes(resolveExpirationMinutes(expiresInMinutes)));
        return paymentLink;
    }

    private String generatePaymentLinkId() {
        return "pay_" + UUID.randomUUID().toString().substring(0, 12);
    }

    private long resolveExpirationMinutes(Integer expiresInMinutes) {
        if (expiresInMinutes == null) {
            return paymentLinkExpirationMinutes;
        }
        if (expiresInMinutes < 5 || expiresInMinutes > maxCustomExpirationMinutes) {
            throw new IllegalArgumentException(
                    "Payment link expiration must stay between 5 and " + maxCustomExpirationMinutes + " minutes.");
        }
        return expiresInMinutes.longValue();
    }

    private String resolveVisibility(String raw) {
        String normalized = raw != null ? raw.trim().toUpperCase() : PaymentLinkVisibility.PRIVATE;
        return PaymentLinkVisibility.PUBLIC.equals(normalized)
                ? PaymentLinkVisibility.PUBLIC
                : PaymentLinkVisibility.PRIVATE;
    }

    private String resolveConfirmationMode(String raw) {
        String normalized = raw != null ? raw.trim().toUpperCase() : PaymentLinkConfirmationMode.MANUAL_REVIEW;
        return PaymentLinkConfirmationMode.AUTO_COMPLETE.equals(normalized)
                ? PaymentLinkConfirmationMode.AUTO_COMPLETE
                : PaymentLinkConfirmationMode.MANUAL_REVIEW;
    }

    private String normalizeReferenceLabel(String referenceLabel) {
        if (referenceLabel == null || referenceLabel.isBlank()) {
            return null;
        }
        String normalized = referenceLabel.trim();
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return sanitized;
        }
        metadata.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .limit(12)
                .forEach(entry -> sanitized.put(
                        trimTo(entry.getKey(), 32),
                        trimTo(entry.getValue(), 120)));
        return sanitized;
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private void validateAmount(BigDecimal amountBtc) {
        if (amountBtc == null || amountBtc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment link amount must be greater than zero.");
        }
    }
}
