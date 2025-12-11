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
import source.wallet.dto.WalletDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    private WalletDTO walletDTO;

    @BeforeEach
    void setUp() {
        user = new UserDataBase();
        user.setUsername("testuser");

        wallet = new WalletEntity();
        wallet.setId(1L);
        wallet.setName("TestWallet");
        wallet.setAddress("hashed-address");
        wallet.setUser(user);

        walletDTO = new WalletDTO();
        walletDTO.setName("TestWallet");
        walletDTO.setPassphrase("test-passphrase-bip39");
    }

    @Test
    @DisplayName("Should save wallet successfully")
    void shouldSaveWalletSuccessfully() {
        doNothing().when(verify).checkPassphraseBip39(anyString());
        when(hash.hash(anyString())).thenReturn("hashed-address");
        when(walletRepository.save(any(WalletEntity.class))).thenReturn(wallet);

        assertDoesNotThrow(() -> walletService.save(wallet));

        verify(verify).checkPassphraseBip39(anyString());
        verify(hash).hash(anyString());
        verify(walletRepository).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should find wallet by name")
    void shouldFindWalletByName() {
        when(walletRepository.findByName("TestWallet")).thenReturn(wallet);

        WalletEntity result = walletService.findByName("TestWallet");

        assertNotNull(result);
        assertEquals("TestWallet", result.getName());
        verify(walletRepository).findByName("TestWallet");
    }

    @Test
    @DisplayName("Should check if wallet exists by name")
    void shouldCheckIfWalletExistsByName() {
        when(walletRepository.existsByName("TestWallet")).thenReturn(true);

        boolean result = walletService.existsByName("TestWallet");

        assertTrue(result);
        verify(walletRepository).existsByName("TestWallet");
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
        List<WalletEntity> wallets = Arrays.asList(wallet);
        when(walletRepository.findByUserId(user.getId())).thenReturn(wallets);
        when(hash.hash(anyString())).thenReturn("hashed-passphrase");
        doNothing().when(walletRepository).delete(any(WalletEntity.class));

        boolean result = walletService.deleteWallet(user.getId(), walletDTO);

        assertTrue(result);
        verify(walletRepository).findByUserId(user.getId());
        verify(hash).hash(anyString());
        verify(walletRepository).delete(wallet);
    }

    @Test
    @DisplayName("Should throw exception when deleting wallet with no wallets")
    void shouldThrowExceptionWhenDeletingWalletWithNoWallets() {
        when(walletRepository.findByUserId(user.getId())).thenReturn(Collections.emptyList());
        when(hash.hash(anyString())).thenReturn("hashed-passphrase");

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletService.deleteWallet(user.getId(), walletDTO);
        });

        verify(walletRepository).findByUserId(user.getId());
        verify(walletRepository, never()).delete(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should return false when wallet to delete not found")
    void shouldReturnFalseWhenWalletToDeleteNotFound() {
        WalletEntity differentWallet = new WalletEntity();
        differentWallet.setName("DifferentWallet");
        List<WalletEntity> wallets = Arrays.asList(differentWallet);
        
        when(walletRepository.findByUserId(user.getId())).thenReturn(wallets);
        when(hash.hash(anyString())).thenReturn("hashed-passphrase");

        boolean result = walletService.deleteWallet(user.getId(), walletDTO);

        assertFalse(result);
        verify(walletRepository).findByUserId(user.getId());
        verify(walletRepository, never()).delete(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should update wallet name successfully")
    void shouldUpdateWalletNameSuccessfully() {
        walletDTO.setNewName("UpdatedWallet");
        List<WalletEntity> wallets = Arrays.asList(wallet);
        
        when(walletRepository.findByUserId(user.getId())).thenReturn(wallets);
        when(walletRepository.existsByName("UpdatedWallet")).thenReturn(false);
        when(walletRepository.save(any(WalletEntity.class))).thenReturn(wallet);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletService.updateWallet(user.getId(), walletDTO);
        });

        verify(walletRepository).findByUserId(user.getId());
        verify(walletRepository).existsByName("UpdatedWallet");
    }

    @Test
    @DisplayName("Should throw exception when updating wallet with no wallets")
    void shouldThrowExceptionWhenUpdatingWalletWithNoWallets() {
        when(walletRepository.findByUserId(user.getId())).thenReturn(Collections.emptyList());

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletService.updateWallet(user.getId(), walletDTO);
        });

        verify(walletRepository).findByUserId(user.getId());
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when new name already exists")
    void shouldThrowExceptionWhenNewNameAlreadyExists() {
        walletDTO.setNewName("ExistingWallet");
        List<WalletEntity> wallets = Arrays.asList(wallet);
        
        when(walletRepository.findByUserId(user.getId())).thenReturn(wallets);
        when(walletRepository.existsByName("ExistingWallet")).thenReturn(true);

        assertThrows(WalletExceptions.WalletNameAlredyExists.class, () -> {
            walletService.updateWallet(user.getId(), walletDTO);
        });

        verify(walletRepository).findByUserId(user.getId());
        verify(walletRepository).existsByName("ExistingWallet");
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when wallet not found for update")
    void shouldThrowExceptionWhenWalletNotFoundForUpdate() {
        WalletEntity differentWallet = new WalletEntity();
        differentWallet.setName("DifferentWallet");
        List<WalletEntity> wallets = Arrays.asList(differentWallet);
        
        when(walletRepository.findByUserId(user.getId())).thenReturn(wallets);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletService.updateWallet(user.getId(), walletDTO);
        });

        verify(walletRepository).findByUserId(user.getId());
    }
}
