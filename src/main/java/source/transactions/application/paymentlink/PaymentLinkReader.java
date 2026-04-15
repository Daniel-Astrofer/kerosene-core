package source.transactions.application.paymentlink;

import org.springframework.stereotype.Service;
import source.transactions.dto.PaymentLinkDTO;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class PaymentLinkReader {

    private final PaymentLinkStore paymentLinkStore;

    public PaymentLinkReader(PaymentLinkStore paymentLinkStore) {
        this.paymentLinkStore = paymentLinkStore;
    }

    public PaymentLinkDTO getPaymentLink(String linkId) {
        return paymentLinkStore.findById(linkId)
                .map(this::expireIfNeeded)
                .orElse(null);
    }

    public PaymentLinkDTO getPublicOnboardingPaymentLink(String linkId) {
        PaymentLinkDTO paymentLink = getPaymentLink(linkId);
        if (!isOnboardingPaymentLink(paymentLink)) {
            return null;
        }
        return paymentLink;
    }

    public List<PaymentLinkDTO> getUserPaymentLinks(Long userId) {
        return paymentLinkStore.findByUserId(userId).stream()
                .map(this::expireIfNeeded)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PaymentLinkDTO::getCreatedAt).reversed())
                .toList();
    }

    public List<PaymentLinkDTO> findByStatus(String status) {
        return paymentLinkStore.findByStatus(status);
    }

    public boolean isOnboardingPaymentLink(PaymentLinkDTO paymentLink) {
        return paymentLink != null
                && PaymentLinkDescription.ONBOARDING_VOUCHER.equals(paymentLink.getDescription())
                && paymentLink.getSessionId() != null
                && !paymentLink.getSessionId().isBlank();
    }

    private PaymentLinkDTO expireIfNeeded(PaymentLinkDTO paymentLink) {
        if (paymentLink == null) {
            return null;
        }

        if (PaymentLinkStatus.PENDING.equals(paymentLink.getStatus())
                && paymentLink.getExpiresAt() != null
                && LocalDateTime.now().isAfter(paymentLink.getExpiresAt())) {
            paymentLink.setStatus(PaymentLinkStatus.EXPIRED);
            paymentLinkStore.save(paymentLink);
        }

        return paymentLink;
    }
}
