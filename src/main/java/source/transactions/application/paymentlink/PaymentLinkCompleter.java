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
public class PaymentLinkCompleter {

    private final PaymentLinkStore paymentLinkStore;
    private final ProcessedTransactionService processedTransactionService;

    public PaymentLinkCompleter(
            PaymentLinkStore paymentLinkStore,
            ProcessedTransactionService processedTransactionService) {
        this.paymentLinkStore = paymentLinkStore;
        this.processedTransactionService = processedTransactionService;
    }

    @Transactional
    public PaymentLinkDTO completePayment(String linkId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkState("Idempotency-Key header is required");
        }
        AtomicReference<PaymentLinkDTO> completed = new AtomicReference<>();
        boolean processed = processedTransactionService.processOnce(
                IdempotencyKeyBuilder.build("payment-link-complete", linkId, idempotencyKey),
                "PAYMENT_LINK_COMPLETE",
                () -> completed.set(completePaymentOnce(linkId)));
        if (!processed) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkState(
                    "Payment link completion already submitted for this idempotency key");
        }
        return completed.get();
    }

    private PaymentLinkDTO completePaymentOnce(String linkId) {
        PaymentLinkDTO paymentLink = paymentLinkStore.findById(linkId)
                .orElseThrow(() -> new PaymentLinkExceptions.PaymentLinkNotFound("Payment link nao encontrado"));

        if (!PaymentLinkStatus.PAID.equals(paymentLink.getStatus())) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkState(
                    "Payment link precisa estar 'paid' para ser completado");
        }

        paymentLink.setStatus(PaymentLinkStatus.COMPLETED);
        paymentLink.setCompletedAt(LocalDateTime.now());
        return paymentLinkStore.save(paymentLink);
    }
}
