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
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.application.service.WalletReader;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.model.WalletMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
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

    private UpdateWalletInteractor updateWalletInteractor;

    @BeforeEach
    void setUp() {
        updateWalletInteractor = new UpdateWalletInteractor(
                new LoadWalletForUpdateHandler(walletReader),
                new ValidateUpdateWalletRequestHandler(),
                new VerifyWalletUpdatePassphraseHandler(walletPersistenceSupport),
                new EnsureWalletNameAvailabilityHandler(walletReader),
                new ApplyWalletNameUpdateHandler(),
                new ApplyWalletXpubUpdateHandler(),
                new PersistWalletUpdateHandler(walletPersistenceSupport));
    }

    @Test
    void updateWalletRenamesAndReconfiguresXpubThroughHandlerChain() {
        WalletEntity wallet = new WalletEntity();
        wallet.setName("TESTWALLET");
        wallet.setWalletMode(WalletMode.KEROSENE);

        when(walletReader.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase(aryEq("test-passphrase-bip39".toCharArray()), any(WalletEntity.class))).thenReturn(true);
        when(walletReader.existsByUserIdAndName(1L, "UPDATEDWALLET")).thenReturn(false);

        updateWalletInteractor.updateWallet(
                new WalletUpdateDTO("test-passphrase-bip39".toCharArray(), "TestWallet", "UpdatedWallet", "xpub661Example", "SELF_CUSTODY"),
                1L);

        assertEquals("UPDATEDWALLET", wallet.getName());
        assertEquals(WalletMode.SELF_CUSTODY, wallet.getWalletMode());
        assertEquals("xpub661Example", wallet.getXpub());
        assertNull(wallet.getDepositAddress());
        assertEquals(-1, wallet.getLastDerivedIndex());
        verify(walletPersistenceSupport).persist(wallet);
    }

    @Test
    void updateWalletClearsXpubStateWhenBlankXpubIsProvided() {
        WalletEntity wallet = new WalletEntity();
        wallet.setName("TESTWALLET");
        wallet.setWalletMode(WalletMode.SELF_CUSTODY);
        wallet.setXpub("old-xpub");
        wallet.setDepositAddress("bc1qold");
        wallet.setLastDerivedIndex(9);

        when(walletReader.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase(aryEq("test-passphrase-bip39".toCharArray()), any(WalletEntity.class))).thenReturn(true);

        updateWalletInteractor.updateWallet(
                new WalletUpdateDTO("test-passphrase-bip39".toCharArray(), "TestWallet", null, "   ", "KEROSENE"),
                1L);

        assertEquals(WalletMode.KEROSENE, wallet.getWalletMode());
        assertNull(wallet.getXpub());
        assertNull(wallet.getDepositAddress());
        assertEquals(-1, wallet.getLastDerivedIndex());
        verify(walletPersistenceSupport).persist(wallet);
    }

    @Test
    void updateWalletRejectsInvalidPassphrase() {
        WalletEntity wallet = new WalletEntity();
        when(walletReader.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase(aryEq("wrong-passphrase".toCharArray()), any(WalletEntity.class))).thenReturn(false);

        assertThrows(
                WalletExceptions.WalletNoExists.class,
                () -> updateWalletInteractor.updateWallet(
                        new WalletUpdateDTO("wrong-passphrase".toCharArray(), "TestWallet", "UpdatedWallet", null),
                        1L));

        verify(walletPersistenceSupport, never()).persist(any(WalletEntity.class));
    }
}
