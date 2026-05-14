package source.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.wallet.application.port.in.DeleteWalletUseCase;
import source.wallet.application.port.in.UpdateWalletUseCase;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.application.service.WalletReader;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.model.WalletEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Tests")
class WalletServiceTest {

    @Mock
    private WalletReader walletReader;
    @Mock
    private WalletPersistenceSupport walletPersistenceSupport;
    @Mock
    private WalletCredentialsPort walletCredentialsPort;
    @Mock
    private UpdateWalletUseCase updateWalletUseCase;
    @Mock
    private DeleteWalletUseCase deleteWalletUseCase;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(
                walletReader,
                walletPersistenceSupport,
                walletCredentialsPort,
                updateWalletUseCase,
                deleteWalletUseCase);
    }

    @Test
    void saveValidatesAndPersistsNewWallet() {
        WalletEntity wallet = new WalletEntity();
        wallet.setPassphraseHash("test-passphrase-bip39");

        walletService.save(wallet);

        verify(walletCredentialsPort).validateBip39Passphrase("test-passphrase-bip39");
        verify(walletPersistenceSupport).persistNew(wallet);
    }

    @Test
    void findByNameDelegatesToReader() {
        WalletEntity wallet = new WalletEntity();
        wallet.setName("MAIN");
        when(walletReader.findByName("Main")).thenReturn(wallet);

        WalletEntity result = walletService.findByName("Main");

        assertEquals("MAIN", result.getName());
        verify(walletReader).findByName("Main");
    }

    @Test
    void findByNameAndUserIdDelegatesToReader() {
        WalletEntity wallet = new WalletEntity();
        wallet.setName("MAIN");
        when(walletReader.findByNameAndUserId("Main", 7L)).thenReturn(wallet);

        WalletEntity result = walletService.findByNameAndUserId("Main", 7L);

        assertEquals("MAIN", result.getName());
        verify(walletReader).findByNameAndUserId("Main", 7L);
    }

    @Test
    void findByIdDelegatesToReader() {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(55L);
        when(walletReader.findById(55L)).thenReturn(wallet);

        WalletEntity result = walletService.findById(55L);

        assertEquals(55L, result.getId());
        verify(walletReader).findById(55L);
    }

    @Test
    void findByPassphraseHashDelegatesToReader() {
        WalletEntity wallet = new WalletEntity();
        wallet.setPassphraseHash("hash");
        when(walletReader.findByPassphraseHash("hash")).thenReturn(wallet);

        WalletEntity result = walletService.findByPassphraseHash("hash");

        assertEquals("hash", result.getPassphraseHash());
        verify(walletReader).findByPassphraseHash("hash");
    }

    @Test
    void findByDepositAddressDelegatesToReader() {
        WalletEntity wallet = new WalletEntity();
        wallet.setDepositAddress("bc1qwallet");
        when(walletReader.findByDepositAddress("bc1qwallet")).thenReturn(wallet);

        WalletEntity result = walletService.findByDepositAddress("bc1qwallet");

        assertEquals("bc1qwallet", result.getDepositAddress());
        verify(walletReader).findByDepositAddress("bc1qwallet");
    }

    @Test
    void findByLightningAddressDelegatesToReader() {
        when(walletReader.findByLightningAddress("lnbc1")).thenReturn(null);

        WalletEntity result = walletService.findByLightningAddress("lnbc1");

        assertNull(result);
        verify(walletReader).findByLightningAddress("lnbc1");
    }

    @Test
    void existsByUserIdAndNameDelegatesToReader() {
        when(walletReader.existsByUserIdAndName(7L, "Main")).thenReturn(true);

        boolean result = walletService.existsByUserIdAndName(7L, "Main");

        assertTrue(result);
        verify(walletReader).existsByUserIdAndName(7L, "Main");
    }

    @Test
    void existsByNameDelegatesToReader() {
        when(walletReader.existsByName("Main")).thenReturn(true);

        boolean result = walletService.existsByName("Main");

        assertTrue(result);
        verify(walletReader).existsByName("Main");
    }

    @Test
    void findByUserIdDelegatesToReader() {
        List<WalletEntity> expected = List.of(new WalletEntity());
        when(walletReader.findByUserId(7L)).thenReturn(expected);

        List<WalletEntity> result = walletService.findByUserId(7L);

        assertEquals(1, result.size());
        verify(walletReader).findByUserId(7L);
    }

    @Test
    void findPrimaryWalletDelegatesToReader() {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(3L);
        when(walletReader.findPrimaryWallet(7L)).thenReturn(wallet);

        WalletEntity result = walletService.findPrimaryWallet(7L);

        assertEquals(3L, result.getId());
        verify(walletReader).findPrimaryWallet(7L);
    }

    @Test
    void incrementLastDerivedIndexDelegatesToPersistenceSupport() {
        when(walletPersistenceSupport.incrementLastDerivedIndex(7L)).thenReturn(4);

        int result = walletService.incrementLastDerivedIndex(7L);

        assertEquals(4, result);
        verify(walletPersistenceSupport).incrementLastDerivedIndex(7L);
    }

    @Test
    void deleteWalletDelegatesToUseCaseAndReturnsTrue() {
        WalletRequestDTO request = new WalletRequestDTO("test-passphrase-bip39", "Main", null);

        boolean result = walletService.deleteWallet(7L, request);

        assertTrue(result);
        verify(deleteWalletUseCase).deleteWallet(request, 7L);
    }

    @Test
    void updateWalletDelegatesToUseCase() {
        WalletUpdateDTO request = new WalletUpdateDTO("test-passphrase-bip39", "Main", "Updated", null);

        walletService.updateWallet(7L, request);

        verify(updateWalletUseCase).updateWallet(request, 7L);
    }
}
