package source.transactions.application.paymentlink;

import source.transactions.dto.PaymentLinkDTO;

public interface PaymentLinkValidationPort {

    void validateConfirmedTransaction(PaymentLinkDTO paymentLink, String txid, String fromAddress);
}
