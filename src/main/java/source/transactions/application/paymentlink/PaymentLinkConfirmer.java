package source.transactions.application.paymentlink;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;

import java.time.LocalDateTime;

@Service
public class PaymentLinkConfirmer {

    private final PaymentLinkStore paymentLinkStore;
    private final PaymentLinkReader paymentLinkReader;
    private final PaymentLinkValidationPort paymentLinkValidationPort;
    private final PaymentLinkCreditPort paymentLinkCreditPort;
    private final PaymentLinkHistoryPort paymentLinkHistoryPort;

    public PaymentLinkConfirmer(
            PaymentLinkStore paymentLinkStore,
            PaymentLinkReader paymentLinkReader,
            PaymentLinkValidationPort paymentLinkValidationPort,
            PaymentLinkCreditPort paymentLinkCreditPort,
            PaymentLinkHistoryPort paymentLinkHistoryPort) {
        this.paymentLinkStore = paymentLinkStore;
        this.paymentLinkReader = paymentLinkReader;
        this.paymentLinkValidationPort = paymentLinkValidationPort;
        this.paymentLinkCreditPort = paymentLinkCreditPort;
        this.paymentLinkHistoryPort = paymentLinkHistoryPort;
    }

    @Transactional
    public PaymentLinkDTO confirmPayment(String linkId, String txid, String fromAddress) {
        PaymentLinkDTO paymentLink = paymentLinkStore.findById(linkId)
                .orElseThrow(() -> new PaymentLinkExceptions.PaymentLinkNotFound("Payment link nao encontrado"));

        if (!PaymentLinkStatus.PENDING.equals(paymentLink.getStatus())) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkState(
                    "Payment link ja foi processado ou expirou");
        }

        if (paymentLink.getExpiresAt() != null && LocalDateTime.now().isAfter(paymentLink.getExpiresAt())) {
            paymentLink.setStatus(PaymentLinkStatus.EXPIRED);
            paymentLinkStore.save(paymentLink);
            throw new PaymentLinkExceptions.PaymentLinkExpired("Payment link expirou");
        }

        paymentLinkValidationPort.validateConfirmedTransaction(paymentLink, txid, fromAddress);

        paymentLink.setStatus(PaymentLinkStatus.PAID);
        paymentLink.setTxid(txid);
        paymentLink.setPaidAt(LocalDateTime.now());
        paymentLinkStore.save(paymentLink);

        if (paymentLinkReader.isOnboardingPaymentLink(paymentLink)) {
            paymentLink.setStatus(PaymentLinkStatus.VERIFYING_ONBOARDING);
            paymentLinkStore.save(paymentLink);
            paymentLinkHistoryPort.markConfirmed(paymentLink, fromAddress);
            return paymentLink;
        }

        if (paymentLinkReader.isAccountActivationPaymentLink(paymentLink)) {
            paymentLink.setStatus(PaymentLinkStatus.VERIFYING_ACTIVATION);
            paymentLinkStore.save(paymentLink);
            paymentLinkHistoryPort.markConfirmed(paymentLink, fromAddress);
            return paymentLink;
        }

        try {
            paymentLinkCreditPort.creditUserWallet(paymentLink);
            if (PaymentLinkConfirmationMode.AUTO_COMPLETE.equals(paymentLink.getConfirmationMode())) {
                paymentLink.setStatus(PaymentLinkStatus.COMPLETED);
                paymentLink.setCompletedAt(LocalDateTime.now());
            }
            paymentLinkStore.save(paymentLink);
            paymentLinkHistoryPort.markConfirmed(paymentLink, fromAddress);
            return paymentLink;
        } catch (RuntimeException ex) {
            paymentLink.setStatus(PaymentLinkStatus.PENDING);
            paymentLink.setTxid(null);
            paymentLink.setPaidAt(null);
            paymentLink.setDepositFeeBtc(null);
            paymentLink.setNetAmountBtc(null);
            paymentLinkStore.save(paymentLink);
            throw new PaymentLinkExceptions.PaymentLinkCreditFailed(
                    "Erro ao creditar saldo: " + ex.getMessage(), ex);
        }
    }
}
