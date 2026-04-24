package source.transactions.application.paymentlink;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;

import java.time.LocalDateTime;

@Service
public class PaymentLinkCanceller {

    private final PaymentLinkStore paymentLinkStore;

    public PaymentLinkCanceller(PaymentLinkStore paymentLinkStore) {
        this.paymentLinkStore = paymentLinkStore;
    }

    @Transactional
    public PaymentLinkDTO cancel(String linkId, String reason) {
        PaymentLinkDTO paymentLink = paymentLinkStore.findById(linkId)
                .orElseThrow(() -> new PaymentLinkExceptions.PaymentLinkNotFound("Payment link nao encontrado"));

        if (!PaymentLinkStatus.PENDING.equals(paymentLink.getStatus())) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkState(
                    "Apenas payment links pendentes podem ser cancelados.");
        }

        paymentLink.setStatus(PaymentLinkStatus.CANCELLED);
        paymentLink.setCancelledAt(LocalDateTime.now());
        paymentLink.setCancelReason(reason != null ? reason.trim() : null);
        return paymentLinkStore.save(paymentLink);
    }
}
