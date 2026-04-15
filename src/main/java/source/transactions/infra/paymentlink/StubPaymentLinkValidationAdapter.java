package source.transactions.infra.paymentlink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkValidationPort;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;

@Component
public class StubPaymentLinkValidationAdapter implements PaymentLinkValidationPort {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentLinkValidationAdapter.class);

    @Override
    public void validateConfirmedTransaction(PaymentLinkDTO paymentLink, String txid, String fromAddress) {
        if (txid == null || txid.isBlank()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction("Transacao nao e valida");
        }

        if (txid.startsWith("mock_tx_")) {
            log.info("Mock payment detected for payment link {}", paymentLink.getId());
            return;
        }

        log.debug("Payment link {} validation still uses a stub adapter.", paymentLink.getId());
    }
}
