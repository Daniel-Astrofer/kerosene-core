package source.payments.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.common.infra.logging.LogSanitizer;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.service.LedgerContract;
import source.payments.dto.PaymentConfirmRequest;
import source.payments.dto.PaymentStatusResponse;
import source.payments.exception.PaymentException;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentIntentEntity;
import source.payments.repository.PaymentIntentRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConfirmServiceTest {

    private PaymentIntentRepository paymentIntentRepository;
    private WalletRepository walletRepository;
    private LedgerContract ledgerService;
    private PaymentExecutionOutboxService paymentExecutionOutboxService;
    private PaymentConfirmService service;

    @BeforeEach
    void setUp() {
        paymentIntentRepository = mock(PaymentIntentRepository.class);
        walletRepository = mock(WalletRepository.class);
        ledgerService = mock(LedgerContract.class);
        PaymentAuditService paymentAuditService = mock(PaymentAuditService.class);
        paymentExecutionOutboxService = mock(PaymentExecutionOutboxService.class);

        service = new PaymentConfirmService(
                paymentIntentRepository,
                walletRepository,
                ledgerService,
                paymentAuditService,
                new PaymentResponseMapper(),
                new PaymentStateMachine(),
                paymentExecutionOutboxService);
    }

    @Test
    void confirmInternalSettlesLedgerOnce() {
        PaymentIntentEntity intent = quotedInternalIntent();
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(paymentIntentRepository.save(intent)).thenReturn(intent);
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(wallet(10L, "MAIN")));
        when(walletRepository.findByUserId(2L)).thenReturn(List.of(wallet(20L, "MAIN")));

        PaymentStatusResponse response = service.confirm(1L, intent.getId(), new PaymentConfirmRequest(
                "idem-1",
                "confirmed",
                20_000L,
                20_000L));

        assertEquals(PaymentEnums.PaymentIntentStatus.SETTLED, response.status());
        verify(ledgerService).updateBalance(eq(10L), eq(new BigDecimal("-0.00020000")), eq(ledgerContext("PAYMENT_INTERNAL_DEBIT", intent)));
        verify(ledgerService).updateBalance(eq(20L), eq(new BigDecimal("0.00020000")), eq(ledgerContext("PAYMENT_INTERNAL_CREDIT", intent)));
    }

    @Test
    void confirmRejectsExpiredQuote() {
        PaymentIntentEntity intent = quotedInternalIntent();
        intent.setQuoteExpiresAt(Instant.now().minusSeconds(60));
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-expired")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> service.confirm(
                1L,
                intent.getId(),
                new PaymentConfirmRequest("idem-expired", "confirmed", 20_000L, 20_000L)));

        assertEquals("QUOTE_EXPIRED", exception.getErrorCode());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmRejectsChangedAcceptedAmounts() {
        PaymentIntentEntity intent = quotedInternalIntent();
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-changed")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class, () -> service.confirm(
                1L,
                intent.getId(),
                new PaymentConfirmRequest("idem-changed", "confirmed", 20_001L, 20_000L)));

        assertEquals("QUOTE_CHANGED", exception.getErrorCode());
    }

    @Test
    void externalConfirmDebitsAndQueuesWithoutCallingProviderInTransaction() {
        PaymentIntentEntity intent = quotedInternalIntent();
        intent.setRail(PaymentEnums.PaymentRail.LIGHTNING);
        intent.setNetworkFeeSats(60L);
        intent.setKeroseneFeeSats(180L);
        intent.setTotalDebitSats(20_240L);
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-ext")).thenReturn(Optional.empty());
        when(paymentIntentRepository.save(intent)).thenReturn(intent);
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(wallet(10L, "MAIN")));

        PaymentStatusResponse response = service.confirm(
                1L,
                intent.getId(),
                new PaymentConfirmRequest("idem-ext", "confirmed", 20_240L, 20_000L));

        assertEquals(PaymentEnums.PaymentIntentStatus.PROCESSING, response.status());
        assertEquals(10L, intent.getLockedWalletId());
        verify(ledgerService).updateBalance(eq(10L), eq(new BigDecimal("-0.00020240")), eq(ledgerContext("PAYMENT_EXTERNAL_LOCK", intent)));
        verify(paymentExecutionOutboxService).enqueue(intent, "idem-ext");
    }

    @Test
    void repeatedConfirmWithSameIdempotencyKeyDoesNotDebitAgain() {
        PaymentIntentEntity intent = quotedInternalIntent();
        intent.setStatus(PaymentEnums.PaymentIntentStatus.SETTLED);
        intent.setIdempotencyKey("idem-settled");
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-settled")).thenReturn(Optional.of(intent));

        PaymentStatusResponse response = service.confirm(1L, intent.getId(), new PaymentConfirmRequest(
                "idem-settled",
                "confirmed",
                20_000L,
                20_000L));

        assertEquals(PaymentEnums.PaymentIntentStatus.SETTLED, response.status());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedInFlightConfirmWithSameIdempotencyKeyDoesNotDebitAgain() {
        PaymentIntentEntity intent = quotedInternalIntent();
        intent.setStatus(PaymentEnums.PaymentIntentStatus.PROCESSING);
        intent.setIdempotencyKey("idem-processing");
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-processing")).thenReturn(Optional.of(intent));

        PaymentStatusResponse response = service.confirm(1L, intent.getId(), new PaymentConfirmRequest(
                "idem-processing",
                "confirmed",
                20_000L,
                20_000L));

        assertEquals(PaymentEnums.PaymentIntentStatus.PROCESSING, response.status());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(paymentExecutionOutboxService, never()).enqueue(any(), any());
    }

    @Test
    void externalInsufficientFundsDoesNotQueueOutbox() {
        PaymentIntentEntity intent = quotedInternalIntent();
        intent.setRail(PaymentEnums.PaymentRail.LIGHTNING);
        intent.setNetworkFeeSats(60L);
        intent.setKeroseneFeeSats(180L);
        intent.setTotalDebitSats(20_240L);
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-no-funds")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(wallet(10L, "MAIN")));
        doThrow(new LedgerExceptions.InsufficientBalanceException("insufficient"))
                .when(ledgerService).updateBalance(
                        eq(10L),
                        eq(new BigDecimal("-0.00020240")),
                        eq(ledgerContext("PAYMENT_EXTERNAL_LOCK", intent.getId(), "idem-no-funds")));

        PaymentException exception = assertThrows(PaymentException.class, () -> service.confirm(
                1L,
                intent.getId(),
                new PaymentConfirmRequest("idem-no-funds", "confirmed", 20_240L, 20_000L)));

        assertEquals("PAYMENT_INSUFFICIENT_FUNDS", exception.getErrorCode());
        verify(paymentExecutionOutboxService, never()).enqueue(any(), any());
    }

    @Test
    void internalReceiverWalletMissingDoesNotDebitSender() {
        PaymentIntentEntity intent = quotedInternalIntent();
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-missing-receiver-wallet")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(wallet(10L, "MAIN")));
        when(walletRepository.findByUserId(2L)).thenReturn(List.of());

        PaymentException exception = assertThrows(PaymentException.class, () -> service.confirm(
                1L,
                intent.getId(),
                new PaymentConfirmRequest("idem-missing-receiver-wallet", "confirmed", 20_000L, 20_000L)));

        assertEquals("PAYMENT_WALLET_NOT_READY", exception.getErrorCode());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reusedIdempotencyKeyFromAnotherIntentIsRejected() {
        PaymentIntentEntity intent = quotedInternalIntent();
        PaymentIntentEntity other = quotedInternalIntent();
        other.setIdempotencyKey("idem-reused");
        when(paymentIntentRepository.findByIdAndSenderUserIdForUpdate(intent.getId(), 1L))
                .thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findByIdempotencyKey("idem-reused")).thenReturn(Optional.of(other));

        PaymentException exception = assertThrows(PaymentException.class, () -> service.confirm(
                1L,
                intent.getId(),
                new PaymentConfirmRequest("idem-reused", "confirmed", 20_000L, 20_000L)));

        assertEquals("PAYMENT_IDEMPOTENCY_KEY_REUSED", exception.getErrorCode());
        verify(ledgerService, never()).updateBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private PaymentIntentEntity quotedInternalIntent() {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(intent, "id", UUID.randomUUID());
        intent.setSenderUserId(1L);
        intent.setReceiverUserId(2L);
        intent.setReceiverDisplayName("@bob");
        intent.setRail(PaymentEnums.PaymentRail.INTERNAL);
        intent.setFeeMode(PaymentEnums.FeeMode.SENDER_PAYS);
        intent.setRequestedAmountFiat(new BigDecimal("100.00"));
        intent.setFiatCurrency("BRL");
        intent.setAsset("BTC");
        intent.setRequestedAmountSats(20_000L);
        intent.setReceiverAmountSats(20_000L);
        intent.setTotalDebitSats(20_000L);
        intent.setNetworkFeeSats(0L);
        intent.setKeroseneFeeSats(0L);
        intent.setFxRate(new BigDecimal("500000.00"));
        intent.setQuoteExpiresAt(Instant.now().plusSeconds(120));
        intent.setStatus(PaymentEnums.PaymentIntentStatus.QUOTED);
        return intent;
    }

    private WalletEntity wallet(Long id, String name) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(id);
        wallet.setName(name);
        wallet.setIsActive(true);
        return wallet;
    }

    private String ledgerContext(String operation, PaymentIntentEntity intent) {
        return ledgerContext(operation, intent.getId(), intent.getIdempotencyKey());
    }

    private String ledgerContext(String operation, UUID paymentIntentId, String idempotencyKey) {
        return operation
                + ":paymentIntent=" + paymentIntentId
                + ":idem=" + LogSanitizer.fingerprint(idempotencyKey);
    }
}
