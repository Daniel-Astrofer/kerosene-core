package source.transactions.application.paymentlink;

import source.transactions.dto.PaymentLinkDTO;

public interface PaymentLinkHistoryPort {

    void recordCreated(PaymentLinkDTO paymentLink);

    void markConfirmed(PaymentLinkDTO paymentLink, String fromAddress);
}
