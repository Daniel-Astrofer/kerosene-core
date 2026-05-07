package source.ledger.application.paymentrequest;

import org.springframework.stereotype.Service;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.orchestrator.TransactionContract;
import source.wallet.model.WalletEntity;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class PayInternalPaymentRequestUseCase {

    private static final long TTL_MINUTES = 30L;

    private final InternalPaymentRequestStore paymentRequestStore;
    private final PaymentRequestReceiverResolver receiverResolver;
    private final TransactionContract transactionContract;
    private final PaymentRequestHistoryService paymentRequestHistoryService;
    private final PaymentRequestNotificationService paymentRequestNotificationService;

    public PayInternalPaymentRequestUseCase(
            InternalPaymentRequestStore paymentRequestStore,
            PaymentRequestReceiverResolver receiverResolver,
            TransactionContract transactionContract,
            PaymentRequestHistoryService paymentRequestHistoryService,
            PaymentRequestNotificationService paymentRequestNotificationService) {
        this.paymentRequestStore = paymentRequestStore;
        this.receiverResolver = receiverResolver;
        this.transactionContract = transactionContract;
        this.paymentRequestHistoryService = paymentRequestHistoryService;
        this.paymentRequestNotificationService = paymentRequestNotificationService;
    }

    public InternalPaymentRequestDTO pay(
            String linkId,
            Long payerUserId,
            String payerWalletName,
            String idempotencyKey,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
        InternalPaymentRequestDTO request = paymentRequestStore.findById(linkId);
        if (request == null) {
            throw new LedgerExceptions.PaymentRequestNotFoundException("Payment request not found or expired.");
        }

        if ("PAID".equals(request.getStatus())) {
            throw new LedgerExceptions.PaymentRequestAlreadyPaidException(
                    "This payment request has already been successfully processed.");
        }

        if (LocalDateTime.now().isAfter(request.getExpiresAt())) {
            request.setStatus("EXPIRED");
            throw new LedgerExceptions.PaymentRequestExpiredException(
                    "This payment request has expired and can no longer be paid.");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("Payment request is in an invalid state: " + request.getStatus());
        }

        if (request.getRequesterUserId().equals(payerUserId)) {
            throw new LedgerExceptions.PaymentRequestSelfPayException(
                    "Operation Denied: You cannot pay a request that you created yourself.");
        }

        WalletEntity receiverWallet = receiverResolver.resolveLockedReceiverWallet(request);

        TransactionDTO transaction = new TransactionDTO();
        transaction.setSender(payerWalletName);
        transaction.setReceiver(receiverWallet.getId().toString());
        transaction.setAmount(request.getAmount());
        transaction.setContext("Payment Link " + linkId);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setRequestTimestamp(System.currentTimeMillis());
        transaction.setTotpCode(totpCode);
        transaction.setPasskeyAssertionJson(passkeyAssertionJson);
        transaction.setConfirmationPassphrase(confirmationPassphrase);

        transactionContract.processTransaction(transaction);

        request.setStatus("PAID");
        request.setPaidAt(LocalDateTime.now());

        paymentRequestHistoryService.markAsConcluded(linkId);
        paymentRequestStore.save(request, TTL_MINUTES, TimeUnit.MINUTES);
        paymentRequestNotificationService.notifyPaid(request);
        return request;
    }
}
