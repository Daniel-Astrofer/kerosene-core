package source.wallet.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.ledger.service.LedgerService;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WalletUseCase Tests")
class WalletUseCaseTest {

    @Mock
    private UserServiceContract userService;

    @Mock
    private WalletService walletService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private SignupVerifier verifier;

    @InjectMocks
    private WalletUseCase walletUseCase;

    private UserDataBase user;
    private WalletEntity wallet;
    private WalletRequestDTO requestDTO;
    private WalletUpdateDTO updateDTO;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("testuser");

        wallet = new WalletEntity();
        wallet.setId(userId);
        wallet.setName("TESTWALLET");
        wallet.setUser(user);

        requestDTO = new WalletRequestDTO("test-passphrase-bip39", "TestWallet");
        updateDTO = new WalletUpdateDTO("test-passphrase-bip39", "TestWallet", "UpdatedWallet");
    }

    @Test
    @DisplayName("Should create wallet successfully")
    void shouldCreateWalletSuccessfully() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        when(walletService.existsByUserIdAndName(userId, "TESTWALLET")).thenReturn(false);
        doNothing().when(walletService).save(any(WalletEntity.class));
        when(ledgerService.createLedger(any(WalletEntity.class), anyString())).thenReturn(null);

        assertDoesNotThrow(() -> walletUseCase.createWallet(requestDTO, userId));

        verify(userService).buscarPorId(userId);
        verify(walletService).existsByUserIdAndName(userId, "TESTWALLET");
        verify(walletService).save(any(WalletEntity.class));
        verify(ledgerService).createLedger(any(WalletEntity.class), eq("Initial ledger for new wallet"));
    }

    @Test
    @DisplayName("Should throw exception when passphrase is not Bip39 during wallet creation")
    void shouldThrowExceptionWhenPassphraseIsNotBip39DuringWalletCreation() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        doThrow(new source.auth.AuthExceptions.InvalidPassphrase("invalid passphrase"))
                .when(verifier).checkPassphraseBip39(any(char[].class));

        assertThrows(source.auth.AuthExceptions.InvalidPassphrase.class, () -> {
            walletUseCase.createWallet(requestDTO, userId);
        });

        verify(verifier).checkPassphraseBip39(requestDTO.passphrase().toCharArray());
        verify(walletService, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when user not found during wallet creation")
    void shouldThrowExceptionWhenUserNotFoundDuringWalletCreation() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            walletUseCase.createWallet(requestDTO, userId);
        });

        verify(userService).buscarPorId(userId);
        verify(walletService, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when wallet name already exists")
    void shouldThrowExceptionWhenWalletNameAlreadyExists() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        when(walletService.existsByUserIdAndName(userId, "TESTWALLET")).thenReturn(true);

        assertThrows(WalletExceptions.WalletNameAlredyExists.class, () -> {
            walletUseCase.createWallet(requestDTO, userId);
        });

        verify(userService).buscarPorId(userId);
        verify(walletService).existsByUserIdAndName(userId, "TESTWALLET");
        verify(walletService, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should delete wallet successfully")
    void shouldDeleteWalletSuccessfully() {
        when(walletService.deleteWallet(userId, requestDTO)).thenReturn(true);

        assertDoesNotThrow(() -> walletUseCase.deleteWallet(requestDTO, userId));

        verify(walletService).deleteWallet(userId, requestDTO);
    }

    @Test
    @DisplayName("Should throw exception when wallet deletion fails")
    void shouldThrowExceptionWhenWalletDeletionFails() {
        when(walletService.deleteWallet(userId, requestDTO)).thenReturn(false);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletUseCase.deleteWallet(requestDTO, userId);
        });

        verify(walletService).deleteWallet(userId, requestDTO);
    }

    @Test
    @DisplayName("Should get all wallets successfully")
    void shouldGetAllWalletsSuccessfully() {
        List<WalletEntity> wallets = Arrays.asList(wallet);

        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        when(walletService.findByUserId(userId)).thenReturn(wallets);

        List<source.wallet.dto.WalletResponseDTO> result = walletUseCase.getAllWallets(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(userService).buscarPorId(userId);
        verify(walletService).findByUserId(userId);
    }

    @Test
    @DisplayName("Should throw exception when user not found during get all wallets")
    void shouldThrowExceptionWhenUserNotFoundDuringGetAllWallets() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            walletUseCase.getAllWallets(userId);
        });

        verify(userService).buscarPorId(userId);
        verify(walletService, never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("Should get wallet by name successfully")
    void shouldGetWalletByNameSuccessfully() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        when(walletService.findByName("TestWallet")).thenReturn(wallet);

        source.wallet.dto.WalletResponseDTO result = walletUseCase.getWalletByName("TestWallet", userId);

        assertNotNull(result);
        assertEquals("TESTWALLET", result.name());
        verify(userService).buscarPorId(userId);
        verify(walletService).findByName("TestWallet");
    }

    @Test
    @DisplayName("Should throw exception when wallet not found by name")
    void shouldThrowExceptionWhenWalletNotFoundByName() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        when(walletService.findByName("TestWallet")).thenReturn(null);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletUseCase.getWalletByName("TestWallet", userId);
        });

        verify(userService).buscarPorId(userId);
        verify(walletService).findByName("TestWallet");
    }

    @Test
    @DisplayName("Should throw exception when wallet does not belong to user")
    void shouldThrowExceptionWhenWalletDoesNotBelongToUser() {
        UserDataBase differentUser = mock(UserDataBase.class);
        when(differentUser.getId()).thenReturn(2L);
        when(differentUser.getUsername()).thenReturn("differentuser");
        wallet.setUser(differentUser);

        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        when(walletService.findByName("TestWallet")).thenReturn(wallet);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletUseCase.getWalletByName("TestWallet", userId);
        });

        verify(userService).buscarPorId(userId);
        verify(walletService).findByName("TestWallet");
    }

    @Test
    @DisplayName("Should update wallet successfully")
    void shouldUpdateWalletSuccessfully() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.of(user));
        doNothing().when(walletService).updateWallet(userId, updateDTO);

        assertDoesNotThrow(() -> walletUseCase.updateWallet(updateDTO, userId));

        verify(userService).buscarPorId(userId);
        verify(walletService).updateWallet(userId, updateDTO);
    }

    @Test
    @DisplayName("Should throw exception when user not found during wallet update")
    void shouldThrowExceptionWhenUserNotFoundDuringWalletUpdate() {
        when(userService.buscarPorId(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            walletUseCase.updateWallet(updateDTO, userId);
        });

        verify(userService).buscarPorId(userId);
        verify(walletService, never()).updateWallet(anyLong(), any());
    }
}
