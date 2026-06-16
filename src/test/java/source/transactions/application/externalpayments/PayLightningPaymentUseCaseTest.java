package source.transactions.application.externalpayments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.LightningPaymentRequestDTO;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningPaymentGateway;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.ProcessedTransactionService;
import source.transactions.service.ExternalProviderOutboxService;
import source.treasury.service.TreasuryService;
import source.wallet.model.WalletEntity;
import source.transactions.model.ExternalProviderOutboxEntity;
import source.auth.model.entity.UserDataBase;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayLightningPaymentUseCase Tests")
class PayLightningPaymentUseCaseTest {

    @Mock private ExternalPaymentsWalletPort walletPort;
    @Mock private ExternalPaymentsLedgerPort ledgerPort;
    @Mock private ExternalTransfersPort externalTransfersPort;
    @Mock private ExternalPaymentsNotificationPort notificationPort;
    @Mock private ExternalPaymentsAuthorizationPort authorizationPort;
    @Mock private LightningPaymentGateway lightningPaymentGateway;
    @Mock private ExternalPaymentsFeePolicy feePolicy;
    @Mock private TreasuryService treasuryService;
    @Mock private ProcessedTransactionService processedTransactionService;
    @Mock private ExternalProviderOutboxService outboxService;

    private PayLightningPaymentUseCase useCase;
    private ExternalPaymentsMath math = new ExternalPaymentsMath("mainnet");

    @BeforeEach
    void setUp() {
        useCase = new PayLightningPaymentUseCase(
                walletPort,
                ledgerPort,
                externalTransfersPort,
                notificationPort,
                authorizationPort,
                lightningPaymentGateway,
                feePolicy,
                math,
                new ExternalTransferFactory(math),
                treasuryService,
                processedTransactionService,
                outboxService,
                "LND"
        );
    }

    @Test
    @DisplayName("Successfully process a lightning payment")
    void testPayLightning_Success() {
        LightningPaymentRequestDTO request = new LightningPaymentRequestDTO(
            "idempotency-123",
            "TestWallet",
            "lnbc1...",
            new BigDecimal("0.001"),
            new BigDecimal("0.0001"),
            "description",
            "123456",
            "{}",
            "passphrase"
        );
        
        when(processedTransactionService.processOnce(anyString(), anyString(), any(Runnable.class))).thenAnswer(inv -> {
            Runnable runnable = inv.getArgument(2);
            runnable.run();
            return true;
        });
        
        UserDataBase user = new UserDataBase();
        setUserId(user, 1L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("TestWallet");
        wallet.setPassphraseHash("hash");
        wallet.setUser(user);
        
        when(walletPort.requireWallet(1L, "TestWallet")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(eq(1L), eq(wallet), anyString(), anyString(), anyString()))
            .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("signature"));
        
        when(feePolicy.resolveLightningReservedFee(any())).thenReturn(new BigDecimal("0.00001"));
        when(feePolicy.calculateWithdrawalFee(anyLong(), any())).thenReturn(new BigDecimal("0.00000"));
        
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        when(externalTransfersPort.save(any())).thenReturn(transfer);
        
        ExternalProviderOutboxEntity outbox = new ExternalProviderOutboxEntity();
        outbox.setId(UUID.randomUUID());
        when(outboxService.enqueue(any(), anyString(), anyString(), anyString())).thenReturn(outbox);
        
        CustodyGateway.PaymentResult paymentResult = new CustodyGateway.PaymentResult(
            "ref-123", "txid-123", "hash-123", "SETTLED", 500L, "{}"
        );
        when(lightningPaymentGateway.payLightning(any())).thenReturn(paymentResult);
        
        ExternalTransferResponseDTO response = useCase.pay(1L, request);
        
        assertNotNull(response);
        verify(ledgerPort).updateBalance(eq(10L), eq(new BigDecimal("-0.00101000")), anyString());
        verify(ledgerPort).updateBalance(eq(10L), eq(new BigDecimal("0.00000500")), eq("LIGHTNING_NETWORK_FEE_REFUND"));
    }

    @Test
    @DisplayName("Lightning routing failure triggers balance compensation and marks transfer as failed")
    void testPayLightning_RoutingFailure() {
        LightningPaymentRequestDTO request = new LightningPaymentRequestDTO(
            "idempotency-failed",
            "TestWallet",
            "lnbc1...",
            new BigDecimal("0.001"),
            new BigDecimal("0.0001"),
            "description",
            "123456",
            "{}",
            "passphrase"
        );
        
        when(processedTransactionService.processOnce(anyString(), anyString(), any(Runnable.class))).thenAnswer(inv -> {
            Runnable runnable = inv.getArgument(2);
            runnable.run();
            return true;
        });
        
        UserDataBase user = new UserDataBase();
        setUserId(user, 1L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("TestWallet");
        wallet.setUser(user);
        
        when(walletPort.requireWallet(1L, "TestWallet")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(anyLong(), any(), anyString(), anyString(), anyString()))
            .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("signature"));
            
        when(feePolicy.resolveLightningReservedFee(any())).thenReturn(new BigDecimal("0.00001"));
        when(feePolicy.calculateWithdrawalFee(anyLong(), any())).thenReturn(new BigDecimal("0.00000"));
        
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        when(externalTransfersPort.save(any())).thenReturn(transfer);
        
        ExternalProviderOutboxEntity outbox = new ExternalProviderOutboxEntity();
        outbox.setId(UUID.randomUUID());
        when(outboxService.enqueue(any(), anyString(), anyString(), anyString())).thenReturn(outbox);
        
        when(lightningPaymentGateway.payLightning(any())).thenThrow(new RuntimeException("NO_ROUTE"));
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            useCase.pay(1L, request);
        });
        
        assertEquals("NO_ROUTE", exception.getMessage());
        
        // Ensure balance is refunded on routing failure
        verify(ledgerPort).updateBalance(eq(10L), eq(new BigDecimal("-0.00101000")), anyString());
        verify(ledgerPort).updateBalance(eq(10L), eq(new BigDecimal("0.00101000")), eq("LIGHTNING_PAYMENT_PROVIDER_FAILURE_COMPENSATION"));
        verify(outboxService).markFailed(outbox.getId(), "NO_ROUTE", false);
    }

    private void setUserId(UserDataBase user, Long id) {
        try {
            java.lang.reflect.Field field = UserDataBase.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
