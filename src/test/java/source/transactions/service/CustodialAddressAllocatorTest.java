package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.common.service.AddressDerivationService;
import source.sovereign.quorum.QuorumSyncService;
import source.transactions.infra.BitcoinCoreRpcClient;
import source.wallet.model.WalletEntity;
import source.wallet.model.WalletMode;
import source.wallet.repository.WalletRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustodialAddressAllocatorTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AddressDerivationService addressDerivationService;

    @Mock
    private QuorumSyncService quorumSyncService;

    @Mock
    private CustodialDerivationCursorService custodialDerivationCursorService;

    @Mock
    private WatchOnlyAddressImportPort watchOnlyAddressImportPort;

    @Mock
    private BitcoinCoreRpcClient bitcoinCoreRpcClient;

    private WalletEntity wallet;

    @BeforeEach
    void setUp() {
        wallet = new WalletEntity();
        wallet.setId(42L);
        wallet.setName("MAIN");
        wallet.setPassphraseHash("hashed-passphrase");
        wallet.setWalletMode(WalletMode.KEROSENE);
        wallet.setLastDerivedIndex(-1);
    }

    @Test
    void allocatesDedicatedAddressFromKeroseneMasterXpub() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                "xpub-master",
                "",
                "KEROSENE_LOCAL",
                false);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(custodialDerivationCursorService.nextIndex(CustodialDerivationCursorService.KEROSENE_BIP84_EXTERNAL))
                .thenReturn(0);
        when(addressDerivationService.deriveAddressDetailsFromXpub("xpub-master", 0))
                .thenReturn(new AddressDerivationService.DerivedAddress("bc1qwallet420", new byte[] { 1, 2, 3 }, 0, false));
        when(quorumSyncService.proposeTransactionToQuorum(any())).thenReturn(true);

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", true);

        assertEquals("bc1qwallet420", allocation.address());
        assertEquals("KEROSENE_QUORUM_BIP84_EXTERNAL_0", allocation.externalReference());
        assertEquals(-1, wallet.getLastDerivedIndex());
        verify(watchOnlyAddressImportPort).importWatchOnlyPublicKey(new byte[] { 1, 2, 3 }, "bc1qwallet420");
        verify(walletRepository).save(wallet);
    }

    @Test
    void reusesCurrentAddressWhenFreshAllocationNotRequested() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                "xpub-master",
                "",
                "KEROSENE_LOCAL",
                false);

        wallet.setDepositAddress("bc1qcurrent");
        wallet.setExternalWalletReference("XPUB_INDEX_9");
        wallet.setLastDerivedIndex(9);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", false);

        assertEquals("bc1qcurrent", allocation.address());
        assertEquals("XPUB_INDEX_9", allocation.externalReference());
        assertTrue(allocation.reused());
        verify(quorumSyncService, never()).proposeTransactionToQuorum(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void usesBitcoinNodeProviderNameWhenAvailable() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                "xpub-master",
                "",
                "KEROSENE_LOCAL",
                false);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(watchOnlyAddressImportPort.providerName()).thenReturn("LND_NEUTRINO");
        when(custodialDerivationCursorService.nextIndex(CustodialDerivationCursorService.KEROSENE_BIP84_EXTERNAL))
                .thenReturn(4);
        when(addressDerivationService.deriveAddressDetailsFromXpub("xpub-master", 4))
                .thenReturn(new AddressDerivationService.DerivedAddress("bc1qlnd", new byte[] { 9 }, 4, false));
        when(quorumSyncService.proposeTransactionToQuorum(any())).thenReturn(true);

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", true);

        assertEquals("bc1qlnd", allocation.address());
        assertEquals("KEROSENE_QUORUM_BIP84_EXTERNAL_4", allocation.externalReference());
        assertEquals("LND_NEUTRINO", allocation.provider());
    }

    @Test
    void allocatesKeroseneCustodyAddressFromBitcoinCoreWalletWhenEnabled() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                bitcoinCoreRpcClient,
                "",
                "",
                "KEROSENE_LOCAL",
                true,
                false);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(bitcoinCoreRpcClient.getNewAddress("wallet:MAIN")).thenReturn("bc1qcorewalletaddress000000000000000000000000");
        when(bitcoinCoreRpcClient.walletName()).thenReturn("kerosene");
        when(quorumSyncService.proposeTransactionToQuorum(any())).thenReturn(true);

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", true);

        assertEquals("bc1qcorewalletaddress000000000000000000000000", allocation.address());
        assertEquals("BITCOIN_CORE_WALLET:kerosene", allocation.externalReference());
        assertEquals("BITCOIN_CORE_WALLET", allocation.provider());
        verify(addressDerivationService, never()).deriveAddressDetailsFromXpub(any(), anyInt());
        verify(watchOnlyAddressImportPort, never()).importWatchOnlyPublicKey(any(), any());
        verify(walletRepository).save(wallet);
    }

    @Test
    void prefersPlatformMasterXpubOverBitcoinCoreWalletForKeroseneDepositAliases() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                bitcoinCoreRpcClient,
                "xpub-master",
                "",
                "KEROSENE_LOCAL",
                true,
                false);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(custodialDerivationCursorService.nextIndex(CustodialDerivationCursorService.KEROSENE_BIP84_EXTERNAL))
                .thenReturn(9);
        when(addressDerivationService.deriveAddressDetailsFromXpub("xpub-master", 9))
                .thenReturn(new AddressDerivationService.DerivedAddress("bc1qplatformalias9", new byte[] { 7 }, 9, false));
        when(quorumSyncService.proposeTransactionToQuorum(any())).thenReturn(true);

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "deposit:MAIN", true);

        assertEquals("bc1qplatformalias9", allocation.address());
        assertEquals("KEROSENE_QUORUM_BIP84_EXTERNAL_9", allocation.externalReference());
        verify(bitcoinCoreRpcClient, never()).getNewAddress(any());
        verify(watchOnlyAddressImportPort).importWatchOnlyPublicKey(new byte[] { 7 }, "bc1qplatformalias9");
    }

    @Test
    void usesLocalDerivedFallbackOnlyWhenExplicitlyEnabled() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                "",
                "",
                "KEROSENE_LOCAL",
                true);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(addressDerivationService.deriveAddress(42L, "hashed-passphrase")).thenReturn("tb1qlocalfallback");
        when(quorumSyncService.proposeTransactionToQuorum(any())).thenReturn(true);

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", false);

        assertEquals("tb1qlocalfallback", allocation.address());
        assertEquals("LOCAL_DERIVED_FALLBACK_42", allocation.externalReference());
        assertEquals("KEROSENE_LOCAL", allocation.provider());
        verify(watchOnlyAddressImportPort, never()).importWatchOnlyPublicKey(any(), any());
        verify(walletRepository).save(wallet);
    }

    @Test
    void rejectsLocalDerivedFallbackForFreshDepositAliases() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                "",
                "",
                "KEROSENE_LOCAL",
                true);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));

        assertThrows(
                source.transactions.exception.ExternalPaymentsExceptions.CustodyProviderUnavailable.class,
                () -> allocator.allocate(7L, wallet, "deposit:MAIN", true));
        verify(walletRepository, never()).save(any());
        verify(addressDerivationService, never()).deriveAddress(42L, "hashed-passphrase");
    }

    @Test
    void rejectsKeroseneAddressAllocationWithoutXpubWhenFallbackDisabled() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                "",
                "",
                "KEROSENE_LOCAL",
                false);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));

        assertThrows(
                source.transactions.exception.ExternalPaymentsExceptions.CustodyProviderUnavailable.class,
                () -> allocator.allocate(7L, wallet, "wallet:MAIN", false));
        verify(walletRepository, never()).save(any());
    }

    @Test
    void derivesSelfCustodyAddressWithoutOverwritingUserXpub() {
        wallet.setWalletMode(WalletMode.SELF_CUSTODY);
        wallet.setXpub("xpub-user");

        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodialDerivationCursorService,
                watchOnlyAddressImportPort,
                "xpub-master",
                "",
                "KEROSENE_LOCAL",
                false);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(addressDerivationService.deriveAddressDetailsFromXpub("xpub-user", 0))
                .thenReturn(new AddressDerivationService.DerivedAddress("tb1qselfcustody0", new byte[] { 4, 5 }, 0, false));

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", true);

        assertEquals("xpub-user", wallet.getXpub());
        assertEquals("tb1qselfcustody0", allocation.address());
        assertEquals("SELF_CUSTODY_BIP84_EXTERNAL_0", allocation.externalReference());
        assertEquals("SELF_CUSTODY_XPUB", allocation.provider());
        verify(quorumSyncService, never()).proposeTransactionToQuorum(any());
    }
}
