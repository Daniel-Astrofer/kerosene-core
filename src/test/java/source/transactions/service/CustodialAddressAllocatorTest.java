package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.common.service.AddressDerivationService;
import source.ledger.sync.QuorumSyncService;
import source.transactions.infra.CustodyGateway;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private CustodyGateway custodyGateway;

    private WalletEntity wallet;

    @BeforeEach
    void setUp() {
        wallet = new WalletEntity();
        wallet.setId(42L);
        wallet.setName("MAIN");
        wallet.setPassphraseHash("hashed-passphrase");
        wallet.setLastDerivedIndex(-1);
    }

    @Test
    void allocatesDedicatedAddressFromKeroseneMasterXpub() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodyGateway,
                "xpub-master",
                "",
                "KEROSENE_LOCAL");

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(addressDerivationService.deriveChildXpub("xpub-master", 42)).thenReturn("xpub-wallet-42");
        when(addressDerivationService.deriveAddressFromXpub("xpub-wallet-42", 0)).thenReturn("bc1qwallet420");
        when(quorumSyncService.proposeTransactionToQuorum(any())).thenReturn(true);

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", true);

        assertEquals("xpub-wallet-42", wallet.getXpub());
        assertEquals("bc1qwallet420", allocation.address());
        assertEquals("XPUB_INDEX_0", allocation.externalReference());
        assertEquals(0, wallet.getLastDerivedIndex());
        verify(walletRepository).save(wallet);
    }

    @Test
    void reusesCurrentAddressWhenFreshAllocationNotRequested() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodyGateway,
                "xpub-master",
                "",
                "KEROSENE_LOCAL");

        wallet.setDepositAddress("bc1qcurrent");
        wallet.setExternalWalletReference("XPUB_INDEX_9");
        wallet.setXpub("xpub-wallet-42");
        wallet.setLastDerivedIndex(9);

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", false);

        assertEquals("bc1qcurrent", allocation.address());
        assertEquals("XPUB_INDEX_9", allocation.externalReference());
        verify(quorumSyncService, never()).proposeTransactionToQuorum(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void usesCustodyProviderWhenLive() {
        CustodialAddressAllocator allocator = new CustodialAddressAllocator(
                walletRepository,
                addressDerivationService,
                quorumSyncService,
                custodyGateway,
                "",
                "",
                "KEROSENE_LOCAL");

        when(walletRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(wallet));
        when(custodyGateway.isLive()).thenReturn(true);
        when(custodyGateway.providerName()).thenReturn("BCX");
        when(custodyGateway.createOnchainAddress(any())).thenReturn(
                new CustodyGateway.GeneratedOnchainAddress("bc1qcustody", "wallet-ref", "provider-ref"));
        when(quorumSyncService.proposeTransactionToQuorum(any())).thenReturn(true);

        CustodialAddressAllocator.Allocation allocation = allocator.allocate(7L, wallet, "wallet:MAIN", true);

        assertEquals("bc1qcustody", allocation.address());
        assertEquals("wallet-ref", allocation.externalReference());
        assertEquals("BCX", allocation.provider());
        verify(addressDerivationService, never()).deriveAddressFromXpub(any(), anyInt());
    }
}
