package source.transactions.application.paymentlink;

import source.transactions.dto.PaymentLinkDTO;

public interface PaymentLinkCreditPort {

    void creditUserWallet(PaymentLinkDTO paymentLink);
}
