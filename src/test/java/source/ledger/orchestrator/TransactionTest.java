package source.ledger.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.AuthExceptions;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.common.observability.FinancialOperationsMetrics;
import source.ledger.application.transaction.AuthenticatedUserPort;
import source.ledger.application.transaction.InternalTransferHistoryPort;
import source.ledger.application.transaction.TransactionIdempotencyPort;
import source.ledger.application.transaction.TransactionLedgerService;
import source.ledger.application.transaction.TransactionNotificationPort;
import source.ledger.application.transaction.TransactionParticipantResolver;
import source.ledger.application.transaction.TransactionProcessingUseCase;
import source.ledger.application.transaction.handler.AuthenticatedSenderHandler;
import source.ledger.application.transaction.handler.TransactionAuthenticationHandler;
import source.ledger.application.transaction.handler.TransactionExecutionHandler;
import source.ledger.application.transaction.handler.TransactionHistoryHandler;
import source.ledger.application.transaction.handler.TransactionIdempotencyHandler;
import source.ledger.application.transaction.handler.TransactionNotificationHandler;
import source.ledger.application.transaction.handler.TransactionTimestampValidationHandler;
import source.ledger.application.transaction.handler.TransactionWalletResolutionHandler;
import source.ledger.dto.TransactionDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.service.LedgerContract;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Orchestrator Tests")
class TransactionTest {

    @Mock
    private AuthenticatedUserPort authenticatedUserPort;

    @Mock
    private TransactionParticipantResolver participantResolver;

    @Mock
    private TransactionalAuthenticationPort authenticationPort;

    @Mock
    private TransactionIdempotencyPort transactionIdempotencyPort;

    @Mock
    private LedgerContract ledgerService;

    @Mock
    private InternalTransferHistoryPort historyPort;

    @Mock
    private TransactionNotificationPort notificationPort;

    @Mock
    private FinancialOperationsMetrics financialMetrics;

    private TransactionProcessingUseCase transactionProcessingUseCase;

    private TransactionDTO transactionDTO;
    private UserDataBase sender;
    private UserDataBase receiver;
    private WalletEntity senderWallet;
    private WalletEntity receiverWallet;
    private LedgerEntity receiverLedger;

    @BeforeEach
    void setUp() throws Exception {
        transactionProcessingUseCase = new TransactionProcessingUseCase(List.of(
                new TransactionTimestampValidationHandler(),
                new AuthenticatedSenderHandler(authenticatedUserPort, participantResolver),
                new TransactionAuthenticationHandler(authenticationPort),
                new TransactionIdempotencyHandler(transactionIdempotencyPort, financialMetrics),
                new TransactionWalletResolutionHandler(participantResolver),
                new TransactionExecutionHandler(new TransactionLedgerService(ledgerService)),
                new TransactionHistoryHandler(historyPort),
                new TransactionNotificationHandler(notificationPort)));

        sender = new UserDataBase();
        setPrivateId(sender, 1L);
        sender.setUsername("sender");
        sender.setPassphrase("sender-passphrase-hash");
        sender.setTOTPSecret("sender-totp-secret");
        sender.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
        sender.setMultisigThreshold(2);

        receiver = new UserDataBase();
        setPrivateId(receiver, 2L);
        receiver.setUsername("receiver");

        senderWallet = new WalletEntity();
        senderWallet.setId(1L);
        senderWallet.setName("SenderWallet");
        senderWallet.setPassphraseHash("sender-addr");
        senderWallet.setUser(sender);

        receiverWallet = new WalletEntity();
        receiverWallet.setId(2L);
        receiverWallet.setName("ReceiverWallet");
        receiverWallet.setPassphraseHash("receiver-addr");
        receiverWallet.setUser(receiver);

        receiverLedger = new LedgerEntity(receiverWallet, "Receiver ledger");
        receiverLedger.setId(2);
        receiverLedger.setBalance(new BigDecimal("100.00"));

        transactionDTO = new TransactionDTO();
        transactionDTO.setSender("SenderWallet");
        transactionDTO.setReceiver("receiver");
        transactionDTO.setAmount(new BigDecimal("50.00"));
        transactionDTO.setContext("Payment for services");
        transactionDTO.setIdempotencyKey("idem-transaction-test");
        transactionDTO.setConfirmationPassphrase("sender-passphrase");
        transactionDTO.setTotpCode("123456");

        lenient().when(authenticatedUserPort.getAuthenticatedUserId()).thenReturn(1L);
        lenient().when(participantResolver.resolveAuthenticatedSender(1L)).thenReturn(sender);
        lenient().when(transactionIdempotencyPort.reserve(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        lenient().when(participantResolver.resolveSenderWallet(sender, "SenderWallet")).thenReturn(senderWallet);
        lenient().when(participantResolver.resolveReceiverWallet("receiver")).thenReturn(receiverWallet);
        lenient().when(ledgerService.updateBalance(anyLong(), any(BigDecimal.class), anyString()))
                .thenReturn(receiverLedger);
    }

    private void setPrivateId(Object target, Long id) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    @Test
    @DisplayName("Should process transaction successfully")
    void shouldProcessTransactionSuccessfully() {
        assertDoesNotThrow(() -> transactionProcessingUseCase.process(transactionDTO));

        verify(ledgerService, times(2)).updateBalance(anyLong(), any(BigDecimal.class), anyString());
        verify(historyPort).recordInternalTransfer(any());
        verify(notificationPort, times(2)).notifyUser(anyLong(), any(source.notification.model.UserNotificationPayload.class));
    }

    @Test
    @DisplayName("Should throw exception when receiver not found")
    void shouldThrowExceptionWhenReceiverNotFound() {
        when(participantResolver.resolveReceiverWallet("receiver"))
                .thenThrow(new LedgerExceptions.ReceiverNotFoundException("receiver not found"));

        assertThrows(LedgerExceptions.ReceiverNotFoundException.class,
                () -> transactionProcessingUseCase.process(transactionDTO));

        verify(ledgerService, never()).updateBalance(anyLong(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("Should use default context when not provided")
    void shouldUseDefaultContextWhenNotProvided() {
        transactionDTO.setContext(null);

        assertDoesNotThrow(() -> transactionProcessingUseCase.process(transactionDTO));

        verify(ledgerService, times(2)).updateBalance(
                anyLong(),
                any(BigDecimal.class),
                eq("Transfer from @sender to @receiver"));
    }

    @Test
    @DisplayName("Should negate amount for debit operation")
    void shouldNegateAmountForDebitOperation() {
        transactionProcessingUseCase.process(transactionDTO);

        verify(ledgerService).updateBalance(
                eq(senderWallet.getId()),
                eq(transactionDTO.getAmount().negate()),
                anyString());
    }

    @Test
    @DisplayName("Should reject negative amount before ledger mutation")
    void shouldRejectNegativeAmountBeforeLedgerMutation() {
        transactionDTO.setAmount(new BigDecimal("-50.00"));

        assertThrows(IllegalArgumentException.class,
                () -> transactionProcessingUseCase.process(transactionDTO));

        verify(ledgerService, never()).updateBalance(anyLong(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("Should require idempotency key before ledger mutation")
    void shouldRequireIdempotencyKeyBeforeLedgerMutation() {
        transactionDTO.setIdempotencyKey(null);

        assertThrows(IllegalArgumentException.class,
                () -> transactionProcessingUseCase.process(transactionDTO));

        verify(transactionIdempotencyPort, never()).reserve(anyString(), anyLong(), any(TimeUnit.class));
        verify(ledgerService, never()).updateBalance(anyLong(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("Should not reserve idempotency key before passkey challenge")
    void shouldNotReserveIdempotencyKeyBeforePasskeyChallenge() {
        transactionDTO.setIdempotencyKey("idem-passkey-retry");
        transactionDTO.setRequestTimestamp(System.currentTimeMillis());

        doThrow(new AuthExceptions.AuthValidationException("PASSKEY_CHALLENGE_REQUIRED:challenge-123"))
                .when(authenticationPort)
                .authorize(any(TransactionalAuthenticationRequest.class));

        AuthExceptions.AuthValidationException ex = assertThrows(
                AuthExceptions.AuthValidationException.class,
                () -> transactionProcessingUseCase.process(transactionDTO));

        assertTrue(ex.getMessage().contains("PASSKEY_CHALLENGE_REQUIRED:challenge-123"));
        verify(transactionIdempotencyPort, never()).reserve(anyString(), anyLong(), any(TimeUnit.class));
        verifyNoInteractions(ledgerService);
    }
}
