package source.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.model.entity.UserDataBase;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.util.Arrays;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Tests")
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private Hasher hash;

    @Mock
    private SignupVerifier verify;

    @InjectMocks
    private WalletService walletService;

    private WalletEntity wallet;
    private UserDataBase user;
    private WalletRequestDTO requestDTO;
    private WalletUpdateDTO updateDTO;

    @BeforeEach
    void setUp() {
        user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("testuser");

        wallet = new WalletEntity();
        wallet.setId(1L);
        wallet.setName("TESTWALLET");
        wallet.setPassphraseHash("hashed-address");
        wallet.setUser(user);

        requestDTO = new WalletRequestDTO("test-passphrase-bip39", "TestWallet");
        updateDTO = new WalletUpdateDTO("test-passphrase-bip39", "TestWallet", "UpdatedWallet");
    }

    @Test
    @DisplayName("Should save wallet successfully")
    void shouldSaveWalletSuccessfully() {
        doNothing().when(verify).checkPassphraseBip39(any(char[].class));
        when(hash.hash(any(char[].class))).thenReturn("hashed-address");
        when(walletRepository.save(any(WalletEntity.class))).thenReturn(wallet);

        assertDoesNotThrow(() -> walletService.save(wallet));

        verify(verify).checkPassphraseBip39(any(char[].class));
        verify(hash).hash(any(char[].class));
        verify(walletRepository).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should find wallet by name")
    void shouldFindWalletByName() {
        when(walletRepository.findByName("TESTWALLET")).thenReturn(wallet);

        WalletEntity result = walletService.findByName("TESTWALLET");

        assertNotNull(result);
        assertEquals("TESTWALLET", result.getName());
        verify(walletRepository).findByName("TESTWALLET");
    }

    @Test
    @DisplayName("Should check if wallet exists by name")
    void shouldCheckIfWalletExistsByName() {
        when(walletRepository.existsByName("TESTWALLET")).thenReturn(true);

        boolean result = walletService.existsByName("TESTWALLET");

        assertTrue(result);
        verify(walletRepository).existsByName("TESTWALLET");
    }

    @Test
    @DisplayName("Should find wallets by user ID")
    void shouldFindWalletsByUserId() {
        List<WalletEntity> wallets = Arrays.asList(wallet);
        when(walletRepository.findByUserId(user.getId())).thenReturn(wallets);

        List<WalletEntity> result = walletService.findByUserId(user.getId());

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(walletRepository).findByUserId(user.getId());
    }

    @Test
    @DisplayName("Should delete wallet successfully")
    void shouldDeleteWalletSuccessfully() {
        when(walletRepository.findByUserIdAndName(user.getId(), "TESTWALLET")).thenReturn(Optional.of(wallet));
        when(hash.hash(any(char[].class))).thenReturn("hashed-address");
        doNothing().when(walletRepository).delete(any(WalletEntity.class));

        boolean result = walletService.deleteWallet(user.getId(), requestDTO);

        assertTrue(result);
        verify(walletRepository).findByUserIdAndName(user.getId(), "TESTWALLET");
        verify(hash).hash(any(char[].class));
        verify(walletRepository).delete(wallet);
    }

    @Test
    @DisplayName("Should throw exception when deleting wallet with no wallets")
    void shouldThrowExceptionWhenDeletingWalletWithNoWallets() {
        when(walletRepository.findByUserIdAndName(user.getId(), "TESTWALLET")).thenReturn(Optional.empty());

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletService.deleteWallet(user.getId(), requestDTO);
        });

        verify(walletRepository).findByUserIdAndName(user.getId(), "TESTWALLET");
        verify(walletRepository, never()).delete(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should return exception when wallet to delete has wrong passphrase")
    void shouldReturnExceptionWhenWalletToDeleteHasWrongPass() {
        when(walletRepository.findByUserIdAndName(user.getId(), "TESTWALLET")).thenReturn(Optional.of(wallet));
        when(hash.hash(any(char[].class))).thenReturn("wrong-passphrase");

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletService.deleteWallet(user.getId(), requestDTO);
        });

        verify(walletRepository).findByUserIdAndName(user.getId(), "TESTWALLET");
        verify(walletRepository, never()).delete(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should update wallet name successfully")
    void shouldUpdateWalletNameSuccessfully() {
        when(walletRepository.findByUserIdAndName(user.getId(), "TESTWALLET")).thenReturn(Optional.of(wallet));
        when(walletRepository.existsByUserIdAndName(user.getId(), "UPDATEDWALLET")).thenReturn(false);
        when(walletRepository.save(any(WalletEntity.class))).thenReturn(wallet);

        assertDoesNotThrow(() -> {
            walletService.updateWallet(user.getId(), updateDTO);
        });

        verify(walletRepository).findByUserIdAndName(user.getId(), "TESTWALLET");
        verify(walletRepository).existsByUserIdAndName(user.getId(), "UPDATEDWALLET");
    }

    @Test
    @DisplayName("Should throw exception when new name already exists")
    void shouldThrowExceptionWhenNewNameAlreadyExists() {
        when(walletRepository.findByUserIdAndName(user.getId(), "TESTWALLET")).thenReturn(Optional.of(wallet));
        when(walletRepository.existsByUserIdAndName(user.getId(), "UPDATEDWALLET")).thenReturn(true);

        assertThrows(WalletExceptions.WalletNameAlredyExists.class, () -> {
            walletService.updateWallet(user.getId(), updateDTO);
        });

        verify(walletRepository).findByUserIdAndName(user.getId(), "TESTWALLET");
        verify(walletRepository).existsByUserIdAndName(user.getId(), "UPDATEDWALLET");
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when wallet not found for update")
    void shouldThrowExceptionWhenWalletNotFoundForUpdate() {
        when(walletRepository.findByUserIdAndName(user.getId(), "TESTWALLET")).thenReturn(Optional.empty());

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletService.updateWallet(user.getId(), updateDTO);
        });

        verify(walletRepository).findByUserIdAndName(user.getId(), "TESTWALLET");
    }
}
