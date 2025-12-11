package source.wallet.orchestrator;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import source.auth.AuthExceptions;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.ledger.service.LedgerService;
import source.wallet.dto.WalletDTO;
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
    private HttpServletRequest request;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WalletUseCase walletUseCase;

    private UserDataBase user;
    private WalletEntity wallet;
    private WalletDTO walletDTO;

    @BeforeEach
    void setUp() {
        user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("testuser");

        wallet = new WalletEntity();
        wallet.setId(1L);
        wallet.setName("TestWallet");
        wallet.setUser(user);

        walletDTO = new WalletDTO();
        walletDTO.setName("TestWallet");
        walletDTO.setPassphrase("test-passphrase-bip39");

        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Should create wallet successfully")
    void shouldCreateWalletSuccessfully() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(walletService.existsByName("TestWallet")).thenReturn(false);
        doNothing().when(walletService).save(any(WalletEntity.class));
        when(ledgerService.createLedger(any(WalletEntity.class), anyString())).thenReturn(null);

        assertDoesNotThrow(() -> walletUseCase.createWallet(walletDTO, request));

        verify(userService).buscarPorId(1L);
        verify(walletService).existsByName("TestWallet");
        verify(walletService).save(any(WalletEntity.class));
        verify(ledgerService).createLedger(any(WalletEntity.class), eq("Initial ledger for new wallet"));
    }

    @Test
    @DisplayName("Should throw exception when user not found during wallet creation")
    void shouldThrowExceptionWhenUserNotFoundDuringWalletCreation() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.empty());

        assertThrows(AuthExceptions.UserNoExists.class, () -> {
            walletUseCase.createWallet(walletDTO, request);
        });

        verify(userService).buscarPorId(1L);
        verify(walletService, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when wallet name already exists")
    void shouldThrowExceptionWhenWalletNameAlreadyExists() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(walletService.existsByName("TestWallet")).thenReturn(true);

        assertThrows(WalletExceptions.WalletNameAlredyExists.class, () -> {
            walletUseCase.createWallet(walletDTO, request);
        });

        verify(userService).buscarPorId(1L);
        verify(walletService).existsByName("TestWallet");
        verify(walletService, never()).save(any(WalletEntity.class));
    }

    @Test
    @DisplayName("Should delete wallet successfully")
    void shouldDeleteWalletSuccessfully() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(walletService.deleteWallet(1L, walletDTO)).thenReturn(true);

        assertDoesNotThrow(() -> walletUseCase.deleteWallet(walletDTO, request));

        verify(walletService).deleteWallet(1L, walletDTO);
    }

    @Test
    @DisplayName("Should throw exception when wallet deletion fails")
    void shouldThrowExceptionWhenWalletDeletionFails() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(walletService.deleteWallet(1L, walletDTO)).thenReturn(false);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletUseCase.deleteWallet(walletDTO, request);
        });

        verify(walletService).deleteWallet(1L, walletDTO);
    }

    @Test
    @DisplayName("Should get all wallets successfully")
    void shouldGetAllWalletsSuccessfully() {
        List<WalletEntity> wallets = Arrays.asList(wallet);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(walletService.findByUserId(1L)).thenReturn(wallets);

        List<WalletEntity> result = walletUseCase.getAllWallets(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(userService).buscarPorId(1L);
        verify(walletService).findByUserId(1L);
    }

    @Test
    @DisplayName("Should throw exception when user not found during get all wallets")
    void shouldThrowExceptionWhenUserNotFoundDuringGetAllWallets() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.empty());

        assertThrows(AuthExceptions.UserNoExists.class, () -> {
            walletUseCase.getAllWallets(request);
        });

        verify(userService).buscarPorId(1L);
        verify(walletService, never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("Should get wallet by name successfully")
    void shouldGetWalletByNameSuccessfully() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(walletService.findByName("TestWallet")).thenReturn(wallet);

        WalletEntity result = walletUseCase.getWalletByName("TestWallet", request);

        assertNotNull(result);
        assertEquals("TestWallet", result.getName());
        verify(userService).buscarPorId(1L);
        verify(walletService).findByName("TestWallet");
    }

    @Test
    @DisplayName("Should throw exception when wallet not found by name")
    void shouldThrowExceptionWhenWalletNotFoundByName() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(walletService.findByName("TestWallet")).thenReturn(null);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletUseCase.getWalletByName("TestWallet", request);
        });

        verify(userService).buscarPorId(1L);
        verify(walletService).findByName("TestWallet");
    }

    @Test
    @DisplayName("Should throw exception when wallet does not belong to user")
    void shouldThrowExceptionWhenWalletDoesNotBelongToUser() {
        UserDataBase differentUser = mock(UserDataBase.class);
        when(differentUser.getId()).thenReturn(2L);
        when(differentUser.getUsername()).thenReturn("differentuser");
        wallet.setUser(differentUser);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(walletService.findByName("TestWallet")).thenReturn(wallet);

        assertThrows(WalletExceptions.WalletNoExists.class, () -> {
            walletUseCase.getWalletByName("TestWallet", request);
        });

        verify(userService).buscarPorId(1L);
        verify(walletService).findByName("TestWallet");
    }

    @Test
    @DisplayName("Should update wallet successfully")
    void shouldUpdateWalletSuccessfully() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        doNothing().when(walletService).updateWallet(1L, walletDTO);

        assertDoesNotThrow(() -> walletUseCase.updateWallet(walletDTO, request));

        verify(userService).buscarPorId(1L);
        verify(walletService).updateWallet(1L, walletDTO);
    }

    @Test
    @DisplayName("Should throw exception when user not found during wallet update")
    void shouldThrowExceptionWhenUserNotFoundDuringWalletUpdate() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");
        when(userService.buscarPorId(1L)).thenReturn(Optional.empty());

        assertThrows(AuthExceptions.UserNoExists.class, () -> {
            walletUseCase.updateWallet(walletDTO, request);
        });

        verify(userService).buscarPorId(1L);
        verify(walletService, never()).updateWallet(anyLong(), any(WalletDTO.class));
    }
}
