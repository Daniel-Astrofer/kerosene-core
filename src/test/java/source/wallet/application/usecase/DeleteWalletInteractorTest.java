package source.wallet.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.wallet.application.handler.delete.DeleteWalletHandler;
import source.wallet.application.handler.delete.LoadWalletForDeletionHandler;
import source.wallet.application.handler.delete.VerifyWalletDeletionPassphraseHandler;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.application.service.WalletReader;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteWalletInteractor Tests")
class DeleteWalletInteractorTest {

    @Mock
    private WalletReader walletReader;
    @Mock
    private WalletPersistenceSupport walletPersistenceSupport;

    private DeleteWalletInteractor deleteWalletInteractor;

    @BeforeEach
    void setUp() {
        deleteWalletInteractor = new DeleteWalletInteractor(
                new LoadWalletForDeletionHandler(walletReader),
                new VerifyWalletDeletionPassphraseHandler(walletPersistenceSupport),
                new DeleteWalletHandler(walletPersistenceSupport));
    }

    @Test
    void deleteWalletDeletesWhenPassphraseMatches() {
        WalletEntity wallet = new WalletEntity();
        when(walletReader.findByNameAndUserId("Main", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase("test-passphrase-bip39", wallet)).thenReturn(true);

        deleteWalletInteractor.deleteWallet(new WalletRequestDTO("test-passphrase-bip39", "Main", null), 1L);

        verify(walletPersistenceSupport).delete(wallet);
    }

    @Test
    void deleteWalletRejectsInvalidPassphrase() {
        WalletEntity wallet = new WalletEntity();
        when(walletReader.findByNameAndUserId("Main", 1L)).thenReturn(wallet);
        when(walletPersistenceSupport.matchesPassphrase("wrong-passphrase", wallet)).thenReturn(false);

        assertThrows(
                WalletExceptions.WalletNoExists.class,
                () -> deleteWalletInteractor.deleteWallet(new WalletRequestDTO("wrong-passphrase", "Main", null), 1L));

        verify(walletPersistenceSupport, never()).delete(any(WalletEntity.class));
    }
}
