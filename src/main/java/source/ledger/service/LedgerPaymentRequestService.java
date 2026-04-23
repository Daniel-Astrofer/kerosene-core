package source.ledger.service;

import org.springframework.stereotype.Service;
import source.ledger.application.paymentrequest.CreateInternalPaymentRequestUseCase;
import source.ledger.application.paymentrequest.GetInternalPaymentRequestUseCase;
import source.ledger.application.paymentrequest.PayInternalPaymentRequestUseCase;
import source.ledger.dto.InternalPaymentRequestDTO;

import java.math.BigDecimal;

@Service
public class LedgerPaymentRequestService {

    private final CreateInternalPaymentRequestUseCase createInternalPaymentRequestUseCase;
    private final GetInternalPaymentRequestUseCase getInternalPaymentRequestUseCase;
    private final PayInternalPaymentRequestUseCase payInternalPaymentRequestUseCase;

    public LedgerPaymentRequestService(
            CreateInternalPaymentRequestUseCase createInternalPaymentRequestUseCase,
            GetInternalPaymentRequestUseCase getInternalPaymentRequestUseCase,
            PayInternalPaymentRequestUseCase payInternalPaymentRequestUseCase) {
        this.createInternalPaymentRequestUseCase = createInternalPaymentRequestUseCase;
        this.getInternalPaymentRequestUseCase = getInternalPaymentRequestUseCase;
        this.payInternalPaymentRequestUseCase = payInternalPaymentRequestUseCase;
    }

    public InternalPaymentRequestDTO createRequest(Long requesterUserId, BigDecimal amount, String receiverWalletName) {
        return createInternalPaymentRequestUseCase.create(requesterUserId, amount, receiverWalletName);
    }

    public InternalPaymentRequestDTO getRequest(String linkId) {
        return getInternalPaymentRequestUseCase.get(linkId);
    }

    public InternalPaymentRequestDTO payRequest(
            String linkId,
            Long payerUserId,
            String payerWalletName,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
        return payInternalPaymentRequestUseCase.pay(
                linkId,
                payerUserId,
                payerWalletName,
                totpCode,
                passkeyAssertionJson,
                confirmationPassphrase);
    }
}
