package source.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.dto.PaymentRequestPublicDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.event.PaymentRequestEventPublisher;
import source.ledger.orchestrator.TransactionContract;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.notification.service.NotificationService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerPaymentRequestServiceTest {

    @Mock
    private RedisTemplate<String, InternalPaymentRequestDTO> redisTemplate;
    @Mock
    private ValueOperations<String, InternalPaymentRequestDTO> valueOperations;
    @Mock
    private WalletContract walletService;
    @Mock
    private UserService userService;
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
        service = new LedgerPaymentRequestService(
                redisTemplate,
                walletService,
                userService,
                transactionOrchestrator,
                notificationService,
                paymentEventPublisher,
                historyRepository);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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

        verify(valueOperations).set(
                eq("internal_payment_req:" + created.getId()),
                eq(created),
                eq(30L),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void payRequestUsesLockedWalletIdInsteadOfClientVisibleDestination() {
        UserDataBase requester = user(10L, "alice");
        WalletEntity receiverWallet = wallet(55L, requester, "MAIN", "bc1qreceiverlockedaddress0000000000000000000000");

        InternalPaymentRequestDTO req = new InternalPaymentRequestDTO();
        req.setId("link-1");
        req.setRequesterUserId(10L);
        req.setReceiverWalletId(55L);
        req.setReceiverWalletName("MAIN");
        req.setDestinationHash("hash-only");
        req.setAmount(new BigDecimal("0.12500000"));
        req.setStatus("PENDING");
        req.setCreatedAt(LocalDateTime.now());
        req.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(valueOperations.get("internal_payment_req:link-1")).thenReturn(req);
        when(walletService.findById(55L)).thenReturn(receiverWallet);

        service.payRequest("link-1", 22L, "PAYER", "123456", null, "passphrase");

        ArgumentCaptor<TransactionDTO> txCaptor = ArgumentCaptor.forClass(TransactionDTO.class);
        verify(transactionOrchestrator).processTransaction(txCaptor.capture());
        TransactionDTO tx = txCaptor.getValue();
        assertEquals("PAYER", tx.getSender());
        assertEquals("55", tx.getReceiver());
        assertEquals(new BigDecimal("0.12500000"), tx.getAmount());
        assertEquals("Payment Link link-1", tx.getContext());

        assertEquals("PAID", req.getStatus());
        verify(valueOperations).set(eq("internal_payment_req:link-1"), eq(req), eq(30L), eq(TimeUnit.MINUTES));
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
