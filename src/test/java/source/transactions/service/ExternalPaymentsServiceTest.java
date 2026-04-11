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
import source.common.service.AddressDerivationService;
import source.ledger.entity.LedgerEntry;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerService;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.MempoolClient;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalTransferRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;
import source.wallet.service.WalletService;

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
    private WalletService walletService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private LedgerTransactionHistoryRepository historyRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AddressDerivationService addressDerivationService;

    @Mock
    private CustodyGateway custodyGateway;

    @Mock
    private ExternalTransferRepository externalTransferRepository;

    @Mock
    private WalletAuthorizationService walletAuthorizationService;

    @Mock
    private MempoolClient mempoolClient;

    @Mock
    private source.notification.service.NotificationService notificationService;

    private ExternalPaymentsService service;

    @BeforeEach
    void setUp() {
        service = new ExternalPaymentsService(
                walletService,
                walletRepository,
                ledgerService,
                historyRepository,
                ledgerEntryRepository,
                addressDerivationService,
                custodyGateway,
                externalTransferRepository,
                walletAuthorizationService,
                mempoolClient,
                notificationService,
                new BigDecimal("0.009"),
                60L,
                "KEROSENE_LOCAL");
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

        when(walletService.findByNameAndUserId("MAIN", 1L)).thenReturn(wallet);
        when(walletAuthorizationService.authorizeOutboundTransfer(eq(1L), eq(wallet), eq("123456"), eq(null), eq("pass")))
                .thenReturn(new WalletAuthorizationService.AuthorizationResult(user, "_MPC_SIGNED_ABC"));
        when(ledgerService.getBalance(10L)).thenReturn(new BigDecimal("1.00000000"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(50L, 20L, 10L, 5L));
        when(custodyGateway.sendOnchain(any())).thenReturn(new CustodyGateway.PaymentResult(
                "provider-ref",
                "txid-123",
                null,
                "PENDING",
                0L,
                "raw"));
        when(custodyGateway.providerName()).thenReturn("BCX");
        when(externalTransferRepository.save(any(ExternalTransferEntity.class)))
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

        verify(ledgerService).updateBalance(10L, new BigDecimal("-0.10094500"), "EXTERNAL_ONCHAIN_PAYMENT:payout");

        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(entryCaptor.capture());
        assertEquals(new BigDecimal("0.00090000"), entryCaptor.getValue().getFeeAmount());
    }
}
