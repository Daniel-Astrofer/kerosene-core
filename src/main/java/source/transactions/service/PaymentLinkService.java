package source.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.transactions.application.paymentlink.PaymentLinkCompleter;
import source.transactions.application.paymentlink.PaymentLinkConfirmer;
import source.transactions.application.paymentlink.PaymentLinkCreator;
import source.transactions.application.paymentlink.PaymentLinkDescription;
import source.transactions.application.paymentlink.PaymentLinkReader;
import source.transactions.application.paymentlink.PaymentLinkStatus;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PaymentLinkService {

    public static final String ONBOARDING_VOUCHER_DESCRIPTION = PaymentLinkDescription.ONBOARDING_VOUCHER;

    private final PaymentLinkCreator paymentLinkCreator;
    private final PaymentLinkReader paymentLinkReader;
    private final PaymentLinkConfirmer paymentLinkConfirmer;
    private final PaymentLinkCompleter paymentLinkCompleter;
    private final PaymentLinkStore paymentLinkStore;
    public PaymentLinkService(
            PaymentLinkCreator paymentLinkCreator,
            PaymentLinkReader paymentLinkReader,
            PaymentLinkConfirmer paymentLinkConfirmer,
            PaymentLinkCompleter paymentLinkCompleter,
            PaymentLinkStore paymentLinkStore) {
        this.paymentLinkCreator = paymentLinkCreator;
        this.paymentLinkReader = paymentLinkReader;
        this.paymentLinkConfirmer = paymentLinkConfirmer;
        this.paymentLinkCompleter = paymentLinkCompleter;
        this.paymentLinkStore = paymentLinkStore;
    }

    public PaymentLinkDTO createPaymentLink(Long userId, BigDecimal amountBtc, String description) {
        return paymentLinkCreator.createForUser(userId, amountBtc, description);
    }

    public PaymentLinkDTO createAccountActivationPaymentLink(Long userId, BigDecimal amountBtc) {
        return paymentLinkCreator.createForAccountActivation(userId, amountBtc);
    }

    public PaymentLinkDTO createOnboardingPaymentLink(String sessionId, BigDecimal amountBtc, String description) {
        return paymentLinkCreator.createForOnboarding(sessionId, amountBtc, description);
    }

    public PaymentLinkDTO getPublicOnboardingPaymentLink(String linkId) {
        return paymentLinkReader.getPublicOnboardingPaymentLink(linkId);
    }

    public PaymentLinkDTO confirmPublicOnboardingPayment(String linkId, String txid, String fromAddress) {
        PaymentLinkDTO paymentLink = getPublicOnboardingPaymentLink(linkId);
        if (paymentLink == null) {
            throw new PaymentLinkExceptions.PaymentLinkNotFound("Onboarding payment link nao encontrado");
        }
        return confirmPayment(linkId, txid, fromAddress);
    }

    public PaymentLinkDTO getPaymentLink(String linkId) {
        return paymentLinkReader.getPaymentLink(linkId);
    }

    public PaymentLinkDTO confirmPayment(String linkId, String txid, String fromAddress) {
        return paymentLinkConfirmer.confirmPayment(linkId, txid, fromAddress);
    }

    public boolean isOnboardingPaymentLink(PaymentLinkDTO paymentLink) {
        return paymentLinkReader.isOnboardingPaymentLink(paymentLink);
    }

    public PaymentLinkDTO completePayment(String linkId) {
        return paymentLinkCompleter.completePayment(linkId);
    }

    public List<PaymentLinkDTO> getUserPaymentLinks(Long userId) {
        return paymentLinkReader.getUserPaymentLinks(userId);
    }

    public void removeFromRedis(String linkId) {
        paymentLinkStore.delete(linkId);
    }
}
