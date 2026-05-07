package source.transactions.application.paymentlink;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.idempotency.IdempotencyKeyBuilder;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;
import source.transactions.service.ProcessedTransactionService;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PaymentLinkConfirmer {

    private final PaymentLinkStore paymentLinkStore;
    private final PaymentLinkReader paymentLinkReader;
    private final PaymentLinkValidationPort paymentLinkValidationPort;
    private final PaymentLinkCreditPort paymentLinkCreditPort;
    private final PaymentLinkHistoryPort paymentLinkHistoryPort;
    private final ProcessedTransactionService processedTransactionService;

    public PaymentLinkConfirmer(
            PaymentLinkStore paymentLinkStore,
            PaymentLinkReader paymentLinkReader,
            PaymentLinkValidationPort paymentLinkValidationPort,
            PaymentLinkCreditPort paymentLinkCreditPort,
            PaymentLinkHistoryPort paymentLinkHistoryPort,
            ProcessedTransactionService processedTransactionService) {
        this.paymentLinkStore = paymentLinkStore;
        this.paymentLinkReader = paymentLinkReader;
        this.paymentLinkValidationPort = paymentLinkValidationPort;
        this.paymentLinkCreditPort = paymentLinkCreditPort;
        this.paymentLinkHistoryPort = paymentLinkHistoryPort;
        this.processedTransactionService = processedTransactionService;
    }

    @Transactional
    public PaymentLinkDTO confirmPayment(String linkId, String txid, String fromAddress, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction("idempotencyKey is required");
        }
        AtomicReference<PaymentLinkDTO> confirmed = new AtomicReference<>();
        boolean processed = processedTransactionService.processOnce(
                IdempotencyKeyBuilder.build("payment-link-confirm", linkId, idempotencyKey),
                "PAYMENT_LINK_CONFIRM",
                () -> confirmed.set(confirmPaymentOnce(linkId, txid, fromAddress)));
        if (!processed) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkState(
                    "Payment link confirmation already submitted for this idempotency key");
        }
        return confirmed.get();
    }

    private PaymentLinkDTO confirmPaymentOnce(String linkId, String txid, String fromAddress) {
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

        AtomicReference<PaymentLinkDTO> settled = new AtomicReference<>();
        boolean settlementReserved = processedTransactionService.processOnce(
                IdempotencyKeyBuilder.build("payment-link-settlement", txid, paymentLink.getDepositAddress()),
                "PAYMENT_LINK_SETTLEMENT",
                () -> settled.set(applyConfirmedPayment(paymentLink, txid, fromAddress)));
        if (!settlementReserved) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkState(
                    "Blockchain transaction output already settled for a payment link");
        }
        return settled.get();
    }

    private PaymentLinkDTO applyConfirmedPayment(PaymentLinkDTO paymentLink, String txid, String fromAddress) {
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
