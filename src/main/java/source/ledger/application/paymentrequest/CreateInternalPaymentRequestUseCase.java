package source.ledger.application.paymentrequest;

import org.springframework.stereotype.Service;
import source.auth.application.service.account.AccountActivationService;
import source.auth.application.service.user.UserService;
import source.common.validation.FinancialAmountValidator;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.exceptions.LedgerExceptions;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CreateInternalPaymentRequestUseCase {

    private static final long TTL_MINUTES = 30L;

    private final InternalPaymentRequestStore paymentRequestStore;
    private final WalletLookupPort walletLookupPort;
    private final UserService userService;
    private final AccountActivationService accountActivationService;
    private final PaymentRequestDestinationHashService destinationHashService;
    private final PaymentRequestHistoryService paymentRequestHistoryService;
    private final PaymentRequestNotificationService paymentRequestNotificationService;

    public CreateInternalPaymentRequestUseCase(
            InternalPaymentRequestStore paymentRequestStore,
            WalletLookupPort walletLookupPort,
            UserService userService,
            AccountActivationService accountActivationService,
            PaymentRequestDestinationHashService destinationHashService,
            PaymentRequestHistoryService paymentRequestHistoryService,
            PaymentRequestNotificationService paymentRequestNotificationService) {
        this.paymentRequestStore = paymentRequestStore;
        this.walletLookupPort = walletLookupPort;
        this.userService = userService;
        this.accountActivationService = accountActivationService;
        this.destinationHashService = destinationHashService;
        this.paymentRequestHistoryService = paymentRequestHistoryService;
        this.paymentRequestNotificationService = paymentRequestNotificationService;
    }

    @org.springframework.transaction.annotation.Transactional
    public InternalPaymentRequestDTO create(Long requesterUserId, BigDecimal amount, String receiverWalletName) {
        try {
            FinancialAmountValidator.requirePositiveBtc(amount, "amount");
        } catch (IllegalArgumentException ex) {
            throw new LedgerExceptions.InvalidAmountException(
                    "O valor da solicitação de pagamento deve ser maior que zero.");
        }

        userService.buscarPorId(requesterUserId).orElseThrow(
                () -> new RuntimeException("Requester user not found"));
        accountActivationService.assertInboundEnabled(requesterUserId);

        WalletEntity wallet = walletLookupPort.findByNameAndUserId(receiverWalletName, requesterUserId);
        if (wallet == null) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet not found or does not belong to you.");
        }

        LocalDateTime now = LocalDateTime.now();
        InternalPaymentRequestDTO request = new InternalPaymentRequestDTO();
        request.setId(UUID.randomUUID().toString());
        request.setRequesterUserId(requesterUserId);
        request.setReceiverWalletId(wallet.getId());
        request.setReceiverWalletName(wallet.getName());
        request.setDestinationHash(destinationHashService.buildDestinationHash(wallet));
        request.setAmount(amount);
        request.setStatus("PENDING");
        request.setCreatedAt(now);
        request.setExpiresAt(now.plusMinutes(TTL_MINUTES));

        paymentRequestStore.save(request, TTL_MINUTES, TimeUnit.MINUTES);
        paymentRequestHistoryService.recordCreated(request);
        paymentRequestNotificationService.notifyCreated(request);
        return request;
    }
}
