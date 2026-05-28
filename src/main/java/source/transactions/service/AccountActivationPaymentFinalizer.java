package source.transactions.service;

import org.springframework.stereotype.Service;
import source.auth.application.service.account.AccountActivationService;
import source.transactions.application.paymentlink.PaymentLinkStatus;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AccountActivationPaymentFinalizer {

    private final PaymentLinkStore paymentLinkStore;
    private final AccountActivationService accountActivationService;

    public AccountActivationPaymentFinalizer(
            PaymentLinkStore paymentLinkStore,
            AccountActivationService accountActivationService) {
        this.paymentLinkStore = paymentLinkStore;
        this.accountActivationService = accountActivationService;
    }

    public PaymentLinkDTO finalizeConfirmedPayment(PaymentLinkDTO paymentLink) {
        if (paymentLink == null || paymentLink.getUserId() == null) {
            throw new IllegalStateException("Account activation payment link is missing its user owner.");
        }
        if (paymentLink.getTxid() == null || paymentLink.getTxid().isBlank()) {
            throw new IllegalStateException("Account activation payment txid is missing.");
        }

        accountActivationService.activateUser(paymentLink.getUserId());
        paymentLink.setStatus(PaymentLinkStatus.COMPLETED);
        paymentLink.setCompletedAt(LocalDateTime.now());
        return paymentLinkStore.save(paymentLink, Duration.ofHours(24));
    }
}
