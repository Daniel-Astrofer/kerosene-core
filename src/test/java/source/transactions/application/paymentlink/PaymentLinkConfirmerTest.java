package source.transactions.application.paymentlink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;
import source.transactions.service.ProcessedTransactionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentLinkConfirmerTest {

    @Mock
    private PaymentLinkStore paymentLinkStore;
    @Mock
    private PaymentLinkReader paymentLinkReader;
    @Mock
    private PaymentLinkValidationPort paymentLinkValidationPort;
    @Mock
    private PaymentLinkCreditPort paymentLinkCreditPort;
    @Mock
    private PaymentLinkHistoryPort paymentLinkHistoryPort;
    @Mock
    private ProcessedTransactionService processedTransactionService;

    @InjectMocks
    private PaymentLinkConfirmer paymentLinkConfirmer;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).when(processedTransactionService).processOnce(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    void shouldConfirmAndCreditRegularPaymentLink() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));
        when(paymentLinkReader.isOnboardingPaymentLink(paymentLink)).thenReturn(false);
        doAnswer(invocation -> {
            PaymentLinkDTO dto = invocation.getArgument(0);
            dto.setGrossAmountBtc(new BigDecimal("1.00000000"));
            dto.setDepositFeeBtc(new BigDecimal("0.00900000"));
            dto.setNetAmountBtc(new BigDecimal("0.99100000"));
            return null;
        }).when(paymentLinkCreditPort).creditUserWallet(paymentLink);

        PaymentLinkDTO confirmed = paymentLinkConfirmer.confirmPayment("pay-1", "tx-1", "sender", "idem-1");

        assertEquals(PaymentLinkStatus.PAID, confirmed.getStatus());
        assertEquals("SETTLED", confirmed.getPaymentIntentStatus());
        assertEquals(true, confirmed.getTerminal());
        assertEquals("tx-1", confirmed.getTxid());
        assertEquals("tx-1", confirmed.getSettlementReference());
        assertEquals(new BigDecimal("1.00000000"), confirmed.getGrossAmountBtc());
        assertEquals(new BigDecimal("0.00900000"), confirmed.getDepositFeeBtc());
        assertEquals(new BigDecimal("0.99100000"), confirmed.getNetAmountBtc());
        verify(paymentLinkValidationPort).validateConfirmedTransaction(paymentLink, "tx-1", "sender");
        verify(paymentLinkStore, times(2)).save(paymentLink);
        verify(paymentLinkCreditPort).creditUserWallet(paymentLink);
        verify(paymentLinkHistoryPort).markConfirmed(paymentLink, "sender");
    }

    @Test
    void shouldMoveOnboardingPaymentToVerificationState() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        paymentLink.setSessionId("signup-session");
        paymentLink.setDescription(PaymentLinkDescription.ONBOARDING_VOUCHER);
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));
        when(paymentLinkReader.isOnboardingPaymentLink(paymentLink)).thenReturn(true);

        PaymentLinkDTO confirmed = paymentLinkConfirmer.confirmPayment("pay-1", "mock_tx_1", "sender", "idem-1");

        assertEquals(PaymentLinkStatus.VERIFYING_ONBOARDING, confirmed.getStatus());
        assertEquals("PROCESSING", confirmed.getPaymentIntentStatus());
        assertEquals(false, confirmed.getTerminal());
        verify(paymentLinkStore, times(2)).save(paymentLink);
        verify(paymentLinkCreditPort, never()).creditUserWallet(any());
        verify(paymentLinkHistoryPort).markConfirmed(paymentLink, "sender");
    }

    @Test
    void shouldMoveAccountActivationPaymentToVerificationState() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        paymentLink.setDescription(PaymentLinkDescription.ACCOUNT_ACTIVATION);
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));
        when(paymentLinkReader.isAccountActivationPaymentLink(paymentLink)).thenReturn(true);

        PaymentLinkDTO confirmed = paymentLinkConfirmer.confirmPayment("pay-1", "mock_tx_1", "sender", "idem-1");

        assertEquals(PaymentLinkStatus.VERIFYING_ACTIVATION, confirmed.getStatus());
        assertEquals("PROCESSING", confirmed.getPaymentIntentStatus());
        assertEquals(false, confirmed.getTerminal());
        verify(paymentLinkStore, times(2)).save(paymentLink);
        verify(paymentLinkCreditPort, never()).creditUserWallet(any());
        verify(paymentLinkHistoryPort).markConfirmed(paymentLink, "sender");
    }

    @Test
    void shouldRollbackToPendingWhenCreditFails() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));
        when(paymentLinkReader.isOnboardingPaymentLink(paymentLink)).thenReturn(false);
        doThrow(new PaymentLinkExceptions.PaymentLinkCreditFailed("ledger down"))
                .when(paymentLinkCreditPort)
                .creditUserWallet(paymentLink);

        PaymentLinkExceptions.PaymentLinkCreditFailed ex = assertThrows(
                PaymentLinkExceptions.PaymentLinkCreditFailed.class,
                () -> paymentLinkConfirmer.confirmPayment("pay-1", "tx-1", "sender", "idem-1"));

        assertEquals(PaymentLinkStatus.PENDING, paymentLink.getStatus());
        assertEquals("Erro ao creditar saldo: ledger down", ex.getMessage());
        assertEquals(null, paymentLink.getDepositFeeBtc());
        assertEquals(null, paymentLink.getNetAmountBtc());
        verify(paymentLinkStore, times(2)).save(paymentLink);
        verify(paymentLinkHistoryPort, never()).markConfirmed(eq(paymentLink), any());
    }

    @Test
    void shouldRejectDuplicateIdempotencyKeyBeforeCrediting() {
        doReturn(false).when(processedTransactionService).processOnce(anyString(), anyString(), any(Runnable.class));

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkState.class,
                () -> paymentLinkConfirmer.confirmPayment("pay-1", "tx-1", "sender", "idem-1"));

        verify(paymentLinkCreditPort, never()).creditUserWallet(any());
    }

    @Test
    void shouldRejectDuplicateBlockchainSettlementBeforeCrediting() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));
        doAnswer(invocation -> {
            String source = invocation.getArgument(1);
            if ("PAYMENT_LINK_SETTLEMENT".equals(source)) {
                return false;
            }
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).when(processedTransactionService).processOnce(anyString(), anyString(), any(Runnable.class));

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkState.class,
                () -> paymentLinkConfirmer.confirmPayment("pay-1", "tx-1", "sender", "idem-1"));

        verify(paymentLinkCreditPort, never()).creditUserWallet(any());
    }

    @Test
    void shouldExpirePendingLinkBeforeValidationOrCredit() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        paymentLink.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));

        assertThrows(
                PaymentLinkExceptions.PaymentLinkExpired.class,
                () -> paymentLinkConfirmer.confirmPayment("pay-1", "tx-1", "sender", "idem-1"));

        assertEquals(PaymentLinkStatus.EXPIRED, paymentLink.getStatus());
        assertEquals("EXPIRED", paymentLink.getPaymentIntentStatus());
        assertEquals(true, paymentLink.getTerminal());
        verify(paymentLinkStore).save(paymentLink);
        verify(paymentLinkValidationPort, never()).validateConfirmedTransaction(any(), anyString(), anyString());
        verify(paymentLinkCreditPort, never()).creditUserWallet(any());
    }

    @Test
    void shouldRejectAlreadyPaidLinkWithoutCreditingAgain() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        paymentLink.setStatus(PaymentLinkStatus.PAID);
        paymentLink.setTxid("tx-paid");
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkState.class,
                () -> paymentLinkConfirmer.confirmPayment("pay-1", "tx-2", "sender", "idem-2"));

        assertEquals("SETTLED", paymentLink.getPaymentIntentStatus());
        assertEquals(true, paymentLink.getTerminal());
        verify(paymentLinkValidationPort, never()).validateConfirmedTransaction(any(), anyString(), anyString());
        verify(paymentLinkCreditPort, never()).creditUserWallet(any());
        verify(paymentLinkStore, never()).save(paymentLink);
    }

    @Test
    void shouldRejectCancelledLinkWithoutCrediting() {
        PaymentLinkDTO paymentLink = pendingPaymentLink();
        paymentLink.setStatus(PaymentLinkStatus.CANCELLED);
        when(paymentLinkStore.findById("pay-1")).thenReturn(Optional.of(paymentLink));

        assertThrows(
                PaymentLinkExceptions.InvalidPaymentLinkState.class,
                () -> paymentLinkConfirmer.confirmPayment("pay-1", "tx-2", "sender", "idem-2"));

        assertEquals("CANCELED", paymentLink.getPaymentIntentStatus());
        assertEquals(true, paymentLink.getTerminal());
        verify(paymentLinkValidationPort, never()).validateConfirmedTransaction(any(), anyString(), anyString());
        verify(paymentLinkCreditPort, never()).creditUserWallet(any());
    }

    private PaymentLinkDTO pendingPaymentLink() {
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId("pay-1");
        paymentLink.setUserId(10L);
        paymentLink.setAmountBtc(new BigDecimal("1.00000000"));
        paymentLink.setDepositAddress("bc1qpaymentlinkaddress");
        paymentLink.setStatus(PaymentLinkStatus.PENDING);
        paymentLink.setCreatedAt(LocalDateTime.now());
        paymentLink.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        paymentLink.setDescription("regular");
        return paymentLink;
    }
}
