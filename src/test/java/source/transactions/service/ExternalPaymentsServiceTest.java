package source.transactions.service;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import source.auth.model.entity.UserDataBase;
import source.transactions.application.externalpayments.CreateLightningInvoiceUseCase;
import source.transactions.application.externalpayments.CancelInboundTransferUseCase;
import source.transactions.application.externalpayments.ExternalPaymentsFeePolicy;
import source.transactions.application.externalpayments.ExternalPaymentsAuthorizationPort;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalPaymentsNotificationPort;
import source.transactions.application.externalpayments.ExternalPaymentsQueryService;
import source.transactions.application.externalpayments.ExternalPaymentsWalletPort;
import source.transactions.application.externalpayments.ExternalTransferFactory;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.application.externalpayments.IssueOnchainAddressUseCase;
import source.transactions.application.externalpayments.PayLightningPaymentUseCase;
import source.transactions.application.externalpayments.SendOnchainPaymentUseCase;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.infra.MempoolClient;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalPaymentsServiceTest {

    @Mock
    private ExternalPaymentsWalletPort walletPort;

    @Mock
    private ExternalPaymentsLedgerPort ledgerPort;

    @Mock
    private ExternalTransfersPort externalTransfersPort;

    @Mock
    private ExternalPaymentsNotificationPort notificationPort;

    @Mock
    private ExternalPaymentsAuthorizationPort authorizationPort;

    @Mock
    private ExternalPaymentsCustodyPort custodyPort;

    @Mock
    private MempoolClient mempoolClient;

    @Mock
    private WalletCardProfileService walletCardProfileService;

    @Mock
    private IssueOnchainAddressUseCase issueOnchainAddressUseCase;

    @Mock
    private CreateLightningInvoiceUseCase createLightningInvoiceUseCase;

    @Mock
    private PayLightningPaymentUseCase payLightningPaymentUseCase;

    @Mock
    private ExternalPaymentsQueryService externalPaymentsQueryService;

    @Mock
    private CancelInboundTransferUseCase cancelInboundTransferUseCase;

    @Mock
    private ProcessedTransactionService processedTransactionService;

    @Mock
    private ExternalProviderOutboxService externalProviderOutboxService;

    private ExternalPaymentsService service;

    @BeforeEach
    void setUp() {
        ExternalPaymentsMath externalPaymentsMath = new ExternalPaymentsMath("testnet");
        ExternalPaymentsFeePolicy externalPaymentsFeePolicy = new ExternalPaymentsFeePolicy(
                mempoolClient,
                walletCardProfileService,
                externalPaymentsMath,
                60L,
                new BigDecimal("0.00100000"),
                new BigDecimal("0.1000"));
        ExternalTransferFactory externalTransferFactory = new ExternalTransferFactory(externalPaymentsMath);
        SendOnchainPaymentUseCase sendOnchainPaymentUseCase = new SendOnchainPaymentUseCase(
                walletPort,
                ledgerPort,
                externalTransfersPort,
                notificationPort,
                authorizationPort,
                custodyPort,
                externalPaymentsFeePolicy,
                externalPaymentsMath,
                externalTransferFactory,
                processedTransactionService,
                externalProviderOutboxService,
                "KEROSENE_LOCAL");

        lenient().doAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).when(processedTransactionService).processOnce(anyString(), anyString(), any(Runnable.class));
        lenient().when(externalProviderOutboxService.enqueue(any(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    source.transactions.model.ExternalProviderOutboxEntity entity =
                            new source.transactions.model.ExternalProviderOutboxEntity();
                    entity.setTransferId(invocation.getArgument(0));
                    entity.setOperationType(invocation.getArgument(1));
                    entity.setIdempotencyKey(invocation.getArgument(2));
                    return entity;
                });
        lenient().when(externalTransfersPort.save(any(ExternalTransferEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service = new ExternalPaymentsService(
                issueOnchainAddressUseCase,
                createLightningInvoiceUseCase,
                cancelInboundTransferUseCase,
                sendOnchainPaymentUseCase,
                payLightningPaymentUseCase,
                externalPaymentsQueryService);
    }

    @Test
    void sendOnchainAppliesZeroPointNinePercentFeeAndPersistsTransfer() {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("alice");

        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setUser(user);
        wallet.setTotpSecret("secret");

        when(walletPort.requireWallet(1L, "MAIN")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(eq(1L), eq(wallet), eq("123456"), eq(null), eq("pass")))
                .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("_MPC_SIGNED_ABC"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(50L, 20L, 10L, 5L));
        when(custodyPort.sendOnchain(any())).thenReturn(new ExternalPaymentsCustodyPort.PaymentResult(
                "provider-ref",
                "txid-123",
                null,
                "PENDING",
                0L,
                "raw"));
        when(custodyPort.providerName()).thenReturn("BCX");
        when(walletCardProfileService.calculateWithdrawalFee(eq(1L), eq(new BigDecimal("0.10000000"))))
                .thenReturn(new BigDecimal("0.00090000"));
        when(externalTransfersPort.save(any(ExternalTransferEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExternalTransferResponseDTO response = service.sendOnchain(
                1L,
                new OnchainSendRequestDTO(
                        "idem-onchain-1",
                        "MAIN",
                        testnetAddress(),
                        new BigDecimal("0.10000000"),
                        "payout",
                        "123456",
                        null,
                        "pass"));

        assertNotNull(response.id());
        assertEquals(new BigDecimal("0.10000000"), response.amountBtc());
        assertEquals(new BigDecimal("0.00090000"), response.platformFeeBtc());
        assertEquals(new BigDecimal("0.00004500"), response.networkFeeBtc());
        assertEquals(new BigDecimal("0.10094500"), response.totalDebitedBtc());
        assertEquals("txid-123", response.externalReference());

        verify(ledgerPort).ensureBalance(10L, new BigDecimal("0.10094500"));
        verify(ledgerPort).updateBalance(10L, new BigDecimal("-0.10094500"), "EXTERNAL_ONCHAIN_PAYMENT:payout");

        ArgumentCaptor<BigDecimal> totalDebitedCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> feeCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledgerPort).recordPlatformFee(eq(response.id()), eq(1L), totalDebitedCaptor.capture(), feeCaptor.capture());
        assertEquals(new BigDecimal("0.10094500"), totalDebitedCaptor.getValue());
        assertEquals(new BigDecimal("0.00090000"), feeCaptor.getValue());
    }

    @Test
    void sendOnchainAppliesBlackCardFeeRate() {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setUser(user);
        wallet.setTotpSecret("secret");

        when(walletPort.requireWallet(1L, "MAIN")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(eq(1L), eq(wallet), eq("123456"), eq(null), eq("pass")))
                .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("_MPC_SIGNED_ABC"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(50L, 20L, 10L, 5L));
        when(custodyPort.sendOnchain(any())).thenReturn(new ExternalPaymentsCustodyPort.PaymentResult(
                "provider-ref",
                "txid-789",
                null,
                "PENDING",
                0L,
                "raw"));
        when(custodyPort.providerName()).thenReturn("BCX");
        when(walletCardProfileService.calculateWithdrawalFee(eq(1L), eq(new BigDecimal("0.10000000"))))
                .thenReturn(new BigDecimal("0.00070000"));
        when(externalTransfersPort.save(any(ExternalTransferEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExternalTransferResponseDTO response = service.sendOnchain(
                1L,
                new OnchainSendRequestDTO(
                        "idem-onchain-2",
                        "MAIN",
                        testnetAddress(),
                        new BigDecimal("0.10000000"),
                        "payout",
                        "123456",
                        null,
                        "pass"));

        assertEquals(new BigDecimal("0.00070000"), response.platformFeeBtc());
        assertEquals(new BigDecimal("0.10074500"), response.totalDebitedBtc());
    }

    @Test
    void sendOnchainCompensatesLedgerWhenProviderFails() {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setUser(user);
        wallet.setTotpSecret("secret");

        when(walletPort.requireWallet(1L, "MAIN")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(eq(1L), eq(wallet), eq("123456"), eq(null), eq("pass")))
                .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("_MPC_SIGNED_ABC"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(50L, 20L, 10L, 5L));
        when(walletCardProfileService.calculateWithdrawalFee(eq(1L), eq(new BigDecimal("0.10000000"))))
                .thenReturn(new BigDecimal("0.00090000"));
        doThrow(new RuntimeException("provider down")).when(custodyPort).sendOnchain(any());

        assertThrows(RuntimeException.class, () -> service.sendOnchain(
                1L,
                new OnchainSendRequestDTO(
                        "idem-provider-fail",
                        "MAIN",
                        testnetAddress(),
                        new BigDecimal("0.10000000"),
                        "payout",
                        "123456",
                        null,
                        "pass")));

        verify(ledgerPort).updateBalance(10L, new BigDecimal("-0.10094500"), "EXTERNAL_ONCHAIN_PAYMENT:payout");
        verify(ledgerPort).updateBalance(10L, new BigDecimal("0.10094500"),
                "ONCHAIN_PAYMENT_PROVIDER_FAILURE_COMPENSATION");
    }

    @Test
    void sendOnchainRejectsWrongNetworkAddressBeforeLedgerMutation() {
        assertThrows(source.transactions.exception.ExternalPaymentsExceptions.InvalidNetworkAddress.class,
                () -> service.sendOnchain(
                        1L,
                        new OnchainSendRequestDTO(
                                "idem-wrong-network",
                                "MAIN",
                                mainnetAddress(),
                                new BigDecimal("0.10000000"),
                                "payout",
                                "123456",
                                null,
                                "pass")));

        verify(walletPort, never()).requireWallet(any(), anyString());
        verify(ledgerPort, never()).updateBalance(any(), any(), any());
        verify(custodyPort, never()).sendOnchain(any());
    }

    @Test
    void sendOnchainRejectsNetworkFeeAboveCapBeforeLedgerMutation() {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setUser(user);

        when(walletPort.requireWallet(1L, "MAIN")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(eq(1L), eq(wallet), eq("123456"), eq(null), eq("pass")))
                .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("_MPC_SIGNED_ABC"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(
                1_000_000L,
                1_000_000L,
                1_000_000L,
                1_000_000L));

        assertThrows(IllegalArgumentException.class,
                () -> service.sendOnchain(
                        1L,
                        new OnchainSendRequestDTO(
                                "idem-high-fee",
                                "MAIN",
                                testnetAddress(),
                                new BigDecimal("0.10000000"),
                                "payout",
                                "123456",
                                null,
                                "pass")));

        verify(ledgerPort, never()).updateBalance(any(), any(), any());
        verify(custodyPort, never()).sendOnchain(any());
    }

    @Test
    void sendOnchainUsesPreflightFeeBeforeDebitingLedger() {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setUser(user);

        when(walletPort.requireWallet(1L, "MAIN")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(eq(1L), eq(wallet), eq("123456"), eq(null), eq("pass")))
                .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("_MPC_SIGNED_ABC"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(50L, 20L, 10L, 5L));
        when(custodyPort.preflightOnchain(any())).thenReturn(new ExternalPaymentsCustodyPort.OnchainFundingPreflight(
                true,
                6_000L,
                "psbt-hash",
                2,
                "BITCOIN_CORE_QUORUM"));
        when(custodyPort.sendOnchain(any())).thenReturn(new ExternalPaymentsCustodyPort.PaymentResult(
                "provider-ref",
                "txid-preflight",
                null,
                "MEMPOOL",
                6_000L,
                "{\"psbtHash\":\"psbt-hash\"}"));
        when(custodyPort.providerName()).thenReturn("BITCOIN_CORE_QUORUM");
        when(walletCardProfileService.calculateWithdrawalFee(eq(1L), eq(new BigDecimal("0.10000000"))))
                .thenReturn(new BigDecimal("0.00090000"));

        ExternalTransferResponseDTO response = service.sendOnchain(
                1L,
                new OnchainSendRequestDTO(
                        "idem-preflight",
                        "MAIN",
                        testnetAddress(),
                        new BigDecimal("0.10000000"),
                        "payout",
                        "123456",
                        null,
                        "pass"));

        assertEquals(new BigDecimal("0.00006000"), response.networkFeeBtc());
        assertEquals(new BigDecimal("0.10096000"), response.totalDebitedBtc());
        verify(ledgerPort).ensureBalance(10L, new BigDecimal("0.10096000"));
        verify(ledgerPort).updateBalance(10L, new BigDecimal("-0.10096000"), "EXTERNAL_ONCHAIN_PAYMENT:payout");
    }

    @Test
    void sendOnchainAmbiguousBroadcastKeepsDebitAndRequiresReconciliation() {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setUser(user);

        when(walletPort.requireWallet(1L, "MAIN")).thenReturn(wallet);
        when(authorizationPort.authorizeOutboundTransfer(eq(1L), eq(wallet), eq("123456"), eq(null), eq("pass")))
                .thenReturn(new ExternalPaymentsAuthorizationPort.AuthorizationResult("_MPC_SIGNED_ABC"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(50L, 20L, 10L, 5L));
        when(walletCardProfileService.calculateWithdrawalFee(eq(1L), eq(new BigDecimal("0.10000000"))))
                .thenReturn(new BigDecimal("0.00090000"));
        when(custodyPort.providerName()).thenReturn("BITCOIN_CORE_QUORUM");
        when(custodyPort.sendOnchain(any())).thenThrow(new ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous(
                "Bitcoin Core broadcast result is ambiguous.",
                "psbt-hash",
                "{\"status\":\"UNKNOWN\",\"combinedPsbtHash\":\"psbt-hash\"}",
                new RuntimeException("timeout")));

        ExternalTransferResponseDTO response = service.sendOnchain(
                1L,
                new OnchainSendRequestDTO(
                        "idem-ambiguous",
                        "MAIN",
                        testnetAddress(),
                        new BigDecimal("0.10000000"),
                        "payout",
                        "123456",
                        null,
                        "pass"));

        assertEquals("AUTO_RESOLUTION_PENDING", response.status());
        assertEquals("psbt-hash", response.externalReference());
        verify(ledgerPort).updateBalance(10L, new BigDecimal("-0.10094500"), "EXTERNAL_ONCHAIN_PAYMENT:payout");
        verify(ledgerPort, never()).updateBalance(10L, new BigDecimal("0.10094500"),
                "ONCHAIN_PAYMENT_PROVIDER_FAILURE_COMPENSATION");
        verify(ledgerPort, never()).recordPlatformFee(any(), any(), any(), any());
        verify(externalProviderOutboxService).markUnknown(
                any(),
                eq("psbt-hash"),
                eq("Bitcoin Core broadcast result is ambiguous."));
        verify(externalTransfersPort, atLeastOnce()).save(any(ExternalTransferEntity.class));
    }

    @Test
    void sendOnchainRejectsDuplicateIdempotencyKeyBeforeLedgerMutation() {
        doReturn(false).when(processedTransactionService).processOnce(anyString(), anyString(), any(Runnable.class));

        assertThrows(source.transactions.exception.ExternalPaymentsExceptions.DuplicateExternalPayment.class,
                () -> service.sendOnchain(
                        1L,
                        new OnchainSendRequestDTO(
                                "idem-duplicate",
                                "MAIN",
                                testnetAddress(),
                                new BigDecimal("0.10000000"),
                                "payout",
                                "123456",
                                null,
                                "pass")));

        verify(ledgerPort, never()).updateBalance(any(), any(), any());
        verify(custodyPort, never()).sendOnchain(any());
    }

    private String testnetAddress() {
        return SegwitAddress.fromHash(TestNet3Params.get(), new ECKey().getPubKeyHash()).toString();
    }

    private String mainnetAddress() {
        return SegwitAddress.fromHash(MainNetParams.get(), new ECKey().getPubKeyHash()).toString();
    }
}
