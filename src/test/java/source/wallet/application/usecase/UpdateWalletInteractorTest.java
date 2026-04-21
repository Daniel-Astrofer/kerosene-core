package source.wallet.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.wallet.application.handler.update.ApplyWalletNameUpdateHandler;
import source.wallet.application.handler.update.ApplyWalletXpubUpdateHandler;
import source.wallet.application.handler.update.EnsureWalletNameAvailabilityHandler;
import source.wallet.application.handler.update.LoadWalletForUpdateHandler;
import source.wallet.application.handler.update.PersistWalletUpdateHandler;
import source.wallet.application.handler.update.ValidateUpdateWalletRequestHandler;
import source.wallet.application.handler.update.VerifyWalletUpdatePassphraseHandler;
import source.wallet.application.port.out.WalletAddressDerivationPort;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.application.service.WalletReader;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateWalletInteractor Tests")
class UpdateWalletInteractorTest {

    @Mock
    private WalletReader walletReader;
    @Mock
    private WalletPersistenceSupport walletPersistenceSupport;
    @Mock
    private WalletAddressDerivationPort walletAddressDerivationPort;

    private UpdateWalletInteractor updateWalletInteractor;

    @BeforeEach
    void setUp() {
        updateWalletInteractor = new UpdateWalletInteractor(
                new LoadWalletForUpdateHandler(walletReader),
                new ValidateUpdateWalletRequestHandler(),
                new VerifyWalletUpdatePassphraseHandler(walletPersistenceSupport),
                new EnsureWalletNameAvailabilityHandler(walletReader),
                new ApplyWalletNameUpdateHandler(),
                new ApplyWalletXpubUpdateHandler(walletAddressDerivationPort),
                new PersistWalletUpdateHandler(walletPersistenceSupport));
    }

    @Test
    void updateWalletRenamesAndReconfiguresXpubThroughHandlerChain() {
        WalletEntity wallet = new WalletEntity();
        wallet.setName("TESTWALLET");

        when(walletReader.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase("test-passphrase-bip39", wallet)).thenReturn(true);
        when(walletReader.existsByUserIdAndName(1L, "UPDATEDWALLET")).thenReturn(false);
        when(walletAddressDerivationPort.deriveAddressFromXpub("xpub661Example", 0)).thenReturn("bc1qnewaddress");

        updateWalletInteractor.updateWallet(
                new WalletUpdateDTO("test-passphrase-bip39", "TestWallet", "UpdatedWallet", "xpub661Example"),
                1L);

        assertEquals("UPDATEDWALLET", wallet.getName());
        assertEquals("xpub661Example", wallet.getXpub());
        assertEquals("bc1qnewaddress", wallet.getDepositAddress());
        assertEquals(0, wallet.getLastDerivedIndex());
        verify(walletPersistenceSupport).persist(wallet);
    }

    @Test
    void updateWalletClearsXpubStateWhenBlankXpubIsProvided() {
        WalletEntity wallet = new WalletEntity();
        wallet.setName("TESTWALLET");
        wallet.setXpub("old-xpub");
        wallet.setDepositAddress("bc1qold");
        wallet.setLastDerivedIndex(9);

        when(walletReader.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase("test-passphrase-bip39", wallet)).thenReturn(true);

        updateWalletInteractor.updateWallet(
                new WalletUpdateDTO("test-passphrase-bip39", "TestWallet", null, "   "),
                1L);

        assertNull(wallet.getXpub());
        assertNull(wallet.getDepositAddress());
        assertEquals(-1, wallet.getLastDerivedIndex());
        verify(walletAddressDerivationPort, never()).deriveAddressFromXpub(any(), any(int.class));
        verify(walletPersistenceSupport).persist(wallet);
    }

    @Test
    void updateWalletRejectsInvalidPassphrase() {
        WalletEntity wallet = new WalletEntity();
        when(walletReader.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase("wrong-passphrase", wallet)).thenReturn(false);

        assertThrows(
                WalletExceptions.WalletNoExists.class,
                () -> updateWalletInteractor.updateWallet(
                        new WalletUpdateDTO("wrong-passphrase", "TestWallet", "UpdatedWallet", null),
                        1L));

        verify(walletPersistenceSupport, never()).persist(any(WalletEntity.class));
    }
}
