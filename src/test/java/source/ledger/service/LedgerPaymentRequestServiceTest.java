package source.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.application.service.account.AccountActivationService;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.application.paymentrequest.CreateInternalPaymentRequestUseCase;
import source.ledger.application.paymentrequest.GetInternalPaymentRequestUseCase;
import source.ledger.application.paymentrequest.InternalPaymentRequestStore;
import source.ledger.application.paymentrequest.PayInternalPaymentRequestUseCase;
import source.ledger.application.paymentrequest.PaymentRequestDestinationHashService;
import source.ledger.application.paymentrequest.PaymentRequestHistoryService;
import source.ledger.application.paymentrequest.PaymentRequestNotificationService;
import source.ledger.application.paymentrequest.PaymentRequestReceiverResolver;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.dto.PaymentRequestPublicDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.event.PaymentRequestEventPublisher;
import source.ledger.orchestrator.TransactionContract;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.notification.service.NotificationService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerPaymentRequestServiceTest {

    @Mock
    private InternalPaymentRequestStore paymentRequestStore;

    @Mock
    private WalletContract walletService;

    @Mock
    private UserService userService;

    @Mock
    private AccountActivationService accountActivationService;

    @Mock
    private TransactionContract transactionOrchestrator;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PaymentRequestEventPublisher paymentEventPublisher;

    @Mock
    private LedgerTransactionHistoryRepository historyRepository;

    private LedgerPaymentRequestService service;

    @BeforeEach
    void setUp() {
        PaymentRequestDestinationHashService destinationHashService = new PaymentRequestDestinationHashService();
        PaymentRequestReceiverResolver receiverResolver =
                new PaymentRequestReceiverResolver(walletService, destinationHashService);
        PaymentRequestHistoryService historyService = new PaymentRequestHistoryService(historyRepository);
        PaymentRequestNotificationService notificationService =
                new PaymentRequestNotificationService(this.notificationService, paymentEventPublisher);

        service = new LedgerPaymentRequestService(
                new CreateInternalPaymentRequestUseCase(
                        paymentRequestStore,
                        walletService,
                        userService,
                        accountActivationService,
                        destinationHashService,
                        historyService,
                        notificationService),
                new GetInternalPaymentRequestUseCase(paymentRequestStore, receiverResolver),
                new PayInternalPaymentRequestUseCase(
                        paymentRequestStore,
                        receiverResolver,
                        transactionOrchestrator,
                        historyService,
                        notificationService));
    }

    @Test
    void createRequestStoresLockedWalletAndExposesOnlyDestinationHashPublicly() {
        UserDataBase requester = user(10L, "alice");
        WalletEntity wallet = wallet(55L, requester, "MAIN", "bc1qreceiverlockedaddress0000000000000000000000");

        when(userService.buscarPorId(10L)).thenReturn(Optional.of(requester));
        when(walletService.findByNameAndUserId("MAIN", 10L)).thenReturn(wallet);

        InternalPaymentRequestDTO created = service.createRequest(
                10L,
                new BigDecimal("0.25000000"),
                "MAIN");

        assertEquals(10L, created.getRequesterUserId());
        assertEquals(55L, created.getReceiverWalletId());
        assertEquals("MAIN", created.getReceiverWalletName());
        assertEquals(new BigDecimal("0.25000000"), created.getAmount());
        assertNotNull(created.getDestinationHash());
        assertEquals(64, created.getDestinationHash().length());

        PaymentRequestPublicDTO publicView = new PaymentRequestPublicDTO(created);
        assertEquals(created.getId(), publicView.getId());
        assertEquals(created.getAmount(), publicView.getAmount());
        assertEquals(created.getDestinationHash(), publicView.getDestinationHash());
        assertFalse(publicView.getDestinationHash().contains("MAIN"));

        verify(paymentRequestStore).save(eq(created), eq(30L), eq(TimeUnit.MINUTES));

        ArgumentCaptor<LedgerTransactionHistory> historyCaptor =
                ArgumentCaptor.forClass(LedgerTransactionHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        LedgerTransactionHistory history = historyCaptor.getValue();
        assertEquals("PAYER_PENDING", history.getSenderIdentifier());
        assertEquals("MAIN", history.getReceiverIdentifier());
        assertEquals("PAYMENT_LINK", history.getTransactionType());
    }

    @Test
    void payRequestUsesLockedWalletIdInsteadOfClientVisibleDestination() {
        UserDataBase requester = user(10L, "alice");
        WalletEntity receiverWallet = wallet(55L, requester, "MAIN", "bc1qreceiverlockedaddress0000000000000000000000");
        String linkId = UUID.randomUUID().toString();

        InternalPaymentRequestDTO req = new InternalPaymentRequestDTO();
        req.setId(linkId);
        req.setRequesterUserId(10L);
        req.setReceiverWalletId(55L);
        req.setReceiverWalletName("MAIN");
        req.setDestinationHash("hash-only");
        req.setAmount(new BigDecimal("0.12500000"));
        req.setStatus("PENDING");
        req.setCreatedAt(LocalDateTime.now());
        req.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(paymentRequestStore.findById(linkId)).thenReturn(req);
        when(walletService.findById(55L)).thenReturn(receiverWallet);

        service.payRequest(linkId, 22L, "PAYER", "pay-request-idem-1", "123456", null, "passphrase");

        ArgumentCaptor<TransactionDTO> txCaptor = ArgumentCaptor.forClass(TransactionDTO.class);
        verify(transactionOrchestrator).processTransaction(txCaptor.capture());
        TransactionDTO tx = txCaptor.getValue();
        assertEquals("PAYER", tx.getSender());
        assertEquals("55", tx.getReceiver());
        assertEquals(new BigDecimal("0.12500000"), tx.getAmount());
        assertEquals("Payment Link " + linkId, tx.getContext());
        assertEquals("pay-request-idem-1", tx.getIdempotencyKey());
        assertNotNull(tx.getRequestTimestamp());

        assertEquals("PAID", req.getStatus());
        verify(historyRepository).updateStatus(UUID.fromString(linkId), "CONCLUDED");
        verify(paymentRequestStore).save(eq(req), eq(30L), eq(TimeUnit.MINUTES));
        verify(this.notificationService).notifyUser(eq(10L), any(source.notification.model.UserNotificationPayload.class));
        verify(paymentEventPublisher).publishPaymentPaid(req);
    }

    private UserDataBase user(Long id, String username) {
        UserDataBase user = new UserDataBase();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        return user;
    }

    private WalletEntity wallet(Long id, UserDataBase user, String name, String depositAddress) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(id);
        wallet.setUser(user);
        wallet.setName(name);
        wallet.setDepositAddress(depositAddress);
        wallet.setPassphraseHash("passphrase-hash-" + id);
        return wallet;
    }
}
