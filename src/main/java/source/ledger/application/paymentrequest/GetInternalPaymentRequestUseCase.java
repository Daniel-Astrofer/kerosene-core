package source.ledger.application.paymentrequest;

import org.springframework.stereotype.Service;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.exceptions.LedgerExceptions;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class GetInternalPaymentRequestUseCase {

    private static final long TTL_MINUTES = 30L;

    private final InternalPaymentRequestStore paymentRequestStore;
    private final PaymentRequestReceiverResolver receiverResolver;

    public GetInternalPaymentRequestUseCase(
            InternalPaymentRequestStore paymentRequestStore,
            PaymentRequestReceiverResolver receiverResolver) {
        this.paymentRequestStore = paymentRequestStore;
        this.receiverResolver = receiverResolver;
    }

    public InternalPaymentRequestDTO get(String linkId) {
        InternalPaymentRequestDTO request = paymentRequestStore.findById(linkId);
        if (request == null) {
            throw new LedgerExceptions.PaymentRequestNotFoundException(
                    "Payment request not found or has been completely removed.");
        }

        if ("PENDING".equals(request.getStatus()) && LocalDateTime.now().isAfter(request.getExpiresAt())) {
            request.setStatus("EXPIRED");
        }

        if ("PENDING".equals(request.getStatus())
                && (request.getReceiverWalletId() == null
                        || request.getDestinationHash() == null
                        || request.getDestinationHash().isBlank())) {
            receiverResolver.resolveLockedReceiverWallet(request);
            paymentRequestStore.save(request, TTL_MINUTES, TimeUnit.MINUTES);
        }

        return request;
    }
}
