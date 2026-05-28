package source.transactions.application.externalpayments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.model.entity.UserDataBase;
import source.common.idempotency.IdempotencyKeyBuilder;
import source.transactions.dto.LightningInvoiceRequestDTO;
import source.transactions.dto.LightningInvoiceResponseDTO;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.ProcessedTransactionService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CreateLightningInvoiceUseCaseTest {

    @Mock
    private ExternalPaymentsWalletPort walletPort;

    @Mock
    private ExternalTransfersPort externalTransfersPort;

    @Mock
    private LightningInvoiceGateway lightningInvoiceGateway;

    @Mock
    private ProcessedTransactionService processedTransactionService;

    private CreateLightningInvoiceUseCase useCase;

    @BeforeEach
    void setUp() {
        ExternalPaymentsMath math = new ExternalPaymentsMath("testnet");
        useCase = new CreateLightningInvoiceUseCase(
                walletPort,
                externalTransfersPort,
                lightningInvoiceGateway,
                math,
                new ExternalTransferFactory(math),
                processedTransactionService,
                "KEROSENE_LOCAL");

        lenient().when(processedTransactionService.processOnce(anyString(), anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable processor = invocation.getArgument(2);
                    processor.run();
                    return true;
                });
    }

    @Test
    void persistsInvoiceWithIdempotencyKeyExpectedAmountAndPaymentHash() {
        WalletEntity wallet = wallet();
        when(walletPort.requireWallet(42L, "MAIN")).thenReturn(wallet);
        when(lightningInvoiceGateway.isLive()).thenReturn(true);
        when(lightningInvoiceGateway.providerName()).thenReturn("LND");
        when(lightningInvoiceGateway.createLightningInvoice(any())).thenReturn(new CustodyGateway.GeneratedLightningInvoice(
                "lnbc1invoice",
                "hash-1",
                "alice@example.com",
                "provider-invoice-1",
                LocalDateTime.now().plusMinutes(15)));
        when(externalTransfersPort.findByPaymentHash("hash-1")).thenReturn(Optional.empty());
        when(externalTransfersPort.save(any(ExternalTransferEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LightningInvoiceResponseDTO response = useCase.create(
                42L,
                new LightningInvoiceRequestDTO(
                        "idem-invoice-1",
                        "MAIN",
                        new BigDecimal("0.00100000"),
                        "deposit",
                        900));

        assertEquals("hash-1", response.paymentHash());
        assertEquals("lnbc1invoice", response.paymentRequest());
        assertEquals(new BigDecimal("0.00100000"), response.amountBtc());

        ArgumentCaptor<ExternalTransferEntity> transferCaptor = ArgumentCaptor.forClass(ExternalTransferEntity.class);
        verify(externalTransfersPort).save(transferCaptor.capture());
        ExternalTransferEntity transfer = transferCaptor.getValue();
        assertEquals("LIGHTNING", transfer.getNetwork());
        assertEquals("INBOUND_INVOICE", transfer.getTransferType());
        assertEquals("PENDING", transfer.getStatus());
        assertEquals("hash-1", transfer.getPaymentHash());
        assertEquals(new BigDecimal("0.00100000"), transfer.getExpectedAmountBtc());
        assertNotNull(transfer.getIdempotencyKey());
        assertFalse(transfer.getIdempotencyKey().isBlank());
    }

    @Test
    void returnsExistingInvoiceForRepeatedIdempotencyKey() {
        String idempotencyRef = IdempotencyKeyBuilder.build("external-lightning-invoice", "42", "idem-retry");
        ExternalTransferEntity existing = existingTransfer();
        existing.setIdempotencyKey(idempotencyRef);
        when(processedTransactionService.processOnce(eq(idempotencyRef), eq("EXTERNAL_LIGHTNING_INVOICE"), any(Runnable.class)))
                .thenReturn(false);
        when(externalTransfersPort.findByIdempotencyKey(idempotencyRef)).thenReturn(Optional.of(existing));

        LightningInvoiceResponseDTO response = useCase.create(
                42L,
                new LightningInvoiceRequestDTO(
                        "idem-retry",
                        "MAIN",
                        new BigDecimal("0.00100000"),
                        "deposit",
                        900));

        assertEquals(existing.getId(), response.transferId());
        assertEquals("hash-existing", response.paymentHash());
        verify(lightningInvoiceGateway, never()).createLightningInvoice(any());
        verify(externalTransfersPort, never()).save(any());
    }

    @Test
    void reusesExistingTransferWhenProviderReturnsExistingPaymentHashForSameWallet() {
        WalletEntity wallet = wallet();
        ExternalTransferEntity existing = existingTransfer();
        when(walletPort.requireWallet(42L, "MAIN")).thenReturn(wallet);
        when(lightningInvoiceGateway.createLightningInvoice(any())).thenReturn(new CustodyGateway.GeneratedLightningInvoice(
                "lnbc1duplicate",
                "hash-existing",
                "alice@example.com",
                "provider-invoice-duplicate",
                LocalDateTime.now().plusMinutes(15)));
        when(externalTransfersPort.findByPaymentHash("hash-existing")).thenReturn(Optional.of(existing));

        LightningInvoiceResponseDTO response = useCase.create(
                42L,
                new LightningInvoiceRequestDTO(
                        "idem-new",
                        "MAIN",
                        new BigDecimal("0.00100000"),
                        "deposit",
                        900));

        assertEquals(existing.getId(), response.transferId());
        assertEquals("hash-existing", response.paymentHash());
        verify(externalTransfersPort, never()).save(any());
    }

    private WalletEntity wallet() {
        UserDataBase user = org.mockito.Mockito.mock(UserDataBase.class);
        lenient().when(user.getId()).thenReturn(42L);
        WalletEntity wallet = new WalletEntity();
        wallet.setId(7L);
        wallet.setName("MAIN");
        wallet.setUser(user);
        return wallet;
    }

    private ExternalTransferEntity existingTransfer() {
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setUserId(42L);
        transfer.setWalletId(7L);
        transfer.setWalletNameSnapshot("MAIN");
        transfer.setNetwork("LIGHTNING");
        transfer.setTransferType("INBOUND_INVOICE");
        transfer.setStatus("PENDING");
        transfer.setProvider("LND");
        transfer.setDestination("alice@example.com");
        transfer.setInvoiceId("provider-invoice-existing");
        transfer.setPaymentHash("hash-existing");
        transfer.setInvoiceData("lnbc1existing");
        transfer.setExpectedAmountBtc(new BigDecimal("0.00100000"));
        transfer.setAmountBtc(new BigDecimal("0.00100000"));
        transfer.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return transfer;
    }
}
