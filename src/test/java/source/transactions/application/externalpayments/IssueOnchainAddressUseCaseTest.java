package source.transactions.application.externalpayments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.model.entity.UserDataBase;
import source.transactions.dto.OnchainAddressAllocationDTO;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.BlockchainAddressWatchService;
import source.transactions.service.CustodialAddressAllocator;
import source.transactions.service.NetworkTransferLifecycleService;
import source.wallet.model.WalletEntity;
import source.wallet.model.WalletMode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueOnchainAddressUseCaseTest {

    @Mock
    private ExternalPaymentsWalletPort walletPort;

    @Mock
    private ExternalTransfersPort externalTransfersPort;

    @Mock
    private CustodialAddressAllocator custodialAddressAllocator;

    @Mock
    private BlockchainAddressWatchService blockchainAddressWatchService;

    @Mock
    private NetworkTransferLifecycleService networkTransferLifecycleService;

    @Mock
    private UserDataBase user;

    private IssueOnchainAddressUseCase useCase;
    private WalletEntity wallet;

    @BeforeEach
    void setUp() {
        useCase = new IssueOnchainAddressUseCase(
                walletPort,
                externalTransfersPort,
                new ExternalTransferFactory(new ExternalPaymentsMath("mainnet")),
                custodialAddressAllocator,
                blockchainAddressWatchService,
                networkTransferLifecycleService,
                "mainnet",
                3,
                false);

        wallet = new WalletEntity();
        wallet.setId(42L);
        wallet.setName("MAIN");
        wallet.setUser(user);
        wallet.setWalletMode(WalletMode.KEROSENE);
    }

    @Test
    void alwaysIssuesFreshKeroseneDepositAliasWithExpectedAmount() {
        when(user.getId()).thenReturn(7L);
        when(walletPort.requireWallet(7L, "MAIN")).thenReturn(wallet);
        when(custodialAddressAllocator.allocate(7L, wallet, "deposit:MAIN", true))
                .thenReturn(new CustodialAddressAllocator.Allocation(
                        "bc1qdedicateddeposit000000000000000000000000",
                        "KEROSENE_QUORUM_BIP84_EXTERNAL_8",
                        "KEROSENE_LOCAL",
                        false));
        when(externalTransfersPort.save(any(ExternalTransferEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OnchainAddressAllocationDTO response = useCase.issue(
                7L,
                new OnchainAddressRequestDTO("MAIN", new BigDecimal("0.123456789")));

        assertEquals("bc1qdedicateddeposit000000000000000000000000", response.onchainAddress());
        assertEquals(new BigDecimal("0.12345678"), response.expectedAmountBtc());
        assertEquals("PENDING", response.transferStatus());

        ArgumentCaptor<ExternalTransferEntity> transferCaptor = ArgumentCaptor.forClass(ExternalTransferEntity.class);
        verify(externalTransfersPort).save(transferCaptor.capture());
        ExternalTransferEntity saved = transferCaptor.getValue();
        assertEquals("ADDRESS_ISSUE", saved.getTransferType());
        assertEquals("ONCHAIN", saved.getNetwork());
        assertEquals(new BigDecimal("0.12345678"), saved.getExpectedAmountBtc());
        assertNull(saved.getAmountBtc());
        assertEquals("bc1qdedicateddeposit000000000000000000000000", saved.getDestination());

        verify(blockchainAddressWatchService).register(
                eq(saved),
                eq("bc1qdedicateddeposit000000000000000000000000"),
                eq("deposit:MAIN"));
        verifyNoInteractions(networkTransferLifecycleService);
    }

    @Test
    void instantSettlementTestModeCreditsExpectedAmountWithoutRegisteringBlockchainWatch() {
        IssueOnchainAddressUseCase localTestUseCase = new IssueOnchainAddressUseCase(
                walletPort,
                externalTransfersPort,
                new ExternalTransferFactory(new ExternalPaymentsMath("mainnet")),
                custodialAddressAllocator,
                blockchainAddressWatchService,
                networkTransferLifecycleService,
                "mainnet",
                3,
                true);

        when(user.getId()).thenReturn(7L);
        when(walletPort.requireWallet(7L, "MAIN")).thenReturn(wallet);
        when(custodialAddressAllocator.allocate(7L, wallet, "deposit:MAIN", true))
                .thenReturn(new CustodialAddressAllocator.Allocation(
                        "bc1qdedicateddeposit000000000000000000000000",
                        "KEROSENE_QUORUM_BIP84_EXTERNAL_8",
                        "KEROSENE_LOCAL",
                        false));
        when(externalTransfersPort.save(any(ExternalTransferEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(networkTransferLifecycleService.reconcileOnchainSettlement(
                any(ExternalTransferEntity.class),
                eq(1_000_000L),
                any(String.class),
                eq(3),
                eq("ONCHAIN_TEST_INSTANT_SETTLEMENT")))
                .thenAnswer(invocation -> {
                    ExternalTransferEntity transfer = invocation.getArgument(0);
                    transfer.setStatus("COMPLETED");
                    transfer.setAmountBtc(new BigDecimal("0.01000000"));
                    transfer.setConfirmations(3);
                    transfer.setBlockchainTxid(invocation.getArgument(2));
                    return transfer;
                });

        OnchainAddressAllocationDTO response = localTestUseCase.issue(
                7L,
                new OnchainAddressRequestDTO("MAIN", new BigDecimal("0.01000000")));

        assertEquals("COMPLETED", response.transferStatus());
        assertEquals(3, response.confirmations());
        assertEquals(new BigDecimal("0.01000000"), response.expectedAmountBtc());

        verify(networkTransferLifecycleService).reconcileOnchainSettlement(
                any(ExternalTransferEntity.class),
                eq(1_000_000L),
                any(String.class),
                eq(3),
                eq("ONCHAIN_TEST_INSTANT_SETTLEMENT"));
        verifyNoInteractions(blockchainAddressWatchService);
    }

    @Test
    void rejectsSelfCustodyWalletsForKeroseneInboundDeposits() {
        wallet.setWalletMode(WalletMode.SELF_CUSTODY);
        when(walletPort.requireWallet(7L, "MAIN")).thenReturn(wallet);

        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.issue(7L, new OnchainAddressRequestDTO("MAIN", new BigDecimal("0.01000000"))));

        verifyNoInteractions(
                custodialAddressAllocator,
                externalTransfersPort,
                blockchainAddressWatchService,
                networkTransferLifecycleService);
    }
}
