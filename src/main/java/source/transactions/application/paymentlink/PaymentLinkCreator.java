package source.transactions.application.paymentlink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.transactions.dto.PaymentLinkDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentLinkCreator {

    private final PaymentLinkStore paymentLinkStore;
    private final PaymentLinkHistoryPort paymentLinkHistoryPort;
    private final String serverDepositAddress;
    private final long paymentLinkExpirationMinutes;

    public PaymentLinkCreator(
            PaymentLinkStore paymentLinkStore,
            PaymentLinkHistoryPort paymentLinkHistoryPort,
            @Value("${bitcoin.deposit-address:1A1z7agoat7F9gq5TF...}") String serverDepositAddress,
            @Value("${bitcoin.payment-link-expiration-minutes:60}") long paymentLinkExpirationMinutes) {
        this.paymentLinkStore = paymentLinkStore;
        this.paymentLinkHistoryPort = paymentLinkHistoryPort;
        this.serverDepositAddress = serverDepositAddress;
        this.paymentLinkExpirationMinutes = paymentLinkExpirationMinutes;
    }

    public PaymentLinkDTO createForUser(Long userId, BigDecimal amountBtc, String description) {
        PaymentLinkDTO paymentLink = newPaymentLink(amountBtc, description);
        paymentLink.setUserId(userId);
        paymentLinkStore.save(paymentLink);
        paymentLinkHistoryPort.recordCreated(paymentLink);
        return paymentLink;
    }

    public PaymentLinkDTO createForOnboarding(String sessionId, BigDecimal amountBtc, String description) {
        PaymentLinkDTO paymentLink = newPaymentLink(amountBtc, description);
        paymentLink.setSessionId(sessionId);
        paymentLinkStore.save(paymentLink);
        return paymentLink;
    }

    private PaymentLinkDTO newPaymentLink(BigDecimal amountBtc, String description) {
        LocalDateTime now = LocalDateTime.now();
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId(generatePaymentLinkId());
        paymentLink.setAmountBtc(amountBtc);
        paymentLink.setGrossAmountBtc(amountBtc);
        paymentLink.setDescription(description);
        paymentLink.setDepositAddress(serverDepositAddress);
        paymentLink.setStatus(PaymentLinkStatus.PENDING);
        paymentLink.setCreatedAt(now);
        paymentLink.setExpiresAt(now.plusMinutes(paymentLinkExpirationMinutes));
        return paymentLink;
    }

    private String generatePaymentLinkId() {
        return "pay_" + UUID.randomUUID().toString().substring(0, 12);
    }
}
