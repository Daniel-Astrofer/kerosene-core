package source.ledger.application.paymentrequest;

import source.ledger.dto.InternalPaymentRequestDTO;

import java.util.concurrent.TimeUnit;

public interface InternalPaymentRequestStore {

    void save(InternalPaymentRequestDTO request, long ttl, TimeUnit unit);

    InternalPaymentRequestDTO findById(String linkId);
}
