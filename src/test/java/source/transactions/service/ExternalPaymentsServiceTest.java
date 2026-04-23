package source.transactions.service;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private ExternalPaymentsService service;

    @BeforeEach
    void setUp() {
        ExternalPaymentsMath externalPaymentsMath = new ExternalPaymentsMath();
        ExternalPaymentsFeePolicy externalPaymentsFeePolicy = new ExternalPaymentsFeePolicy(
                mempoolClient,
                walletCardProfileService,
                externalPaymentsMath,
                60L);
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
                "KEROSENE_LOCAL");

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
                        "MAIN",
                        "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
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
                        "MAIN",
                        "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                        new BigDecimal("0.10000000"),
                        "payout",
                        "123456",
                        null,
                        "pass"));

        assertEquals(new BigDecimal("0.00070000"), response.platformFeeBtc());
        assertEquals(new BigDecimal("0.10074500"), response.totalDebitedBtc());
    }
}
