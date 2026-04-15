package source.transactions.application.paymentlink;

import org.springframework.stereotype.Service;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;

import java.time.LocalDateTime;

@Service
public class PaymentLinkCompleter {

    private final PaymentLinkStore paymentLinkStore;

    public PaymentLinkCompleter(PaymentLinkStore paymentLinkStore) {
        this.paymentLinkStore = paymentLinkStore;
    }

    public PaymentLinkDTO completePayment(String linkId) {
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
