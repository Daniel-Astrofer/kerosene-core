package source.wallet.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.wallet.application.port.in.CreateWalletUseCase;
import source.wallet.application.port.in.DeleteWalletUseCase;
import source.wallet.application.port.in.QueryWalletUseCase;
import source.wallet.application.port.in.UpdateWalletUseCase;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.dto.WalletUpdateDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletUseCase Tests")
class WalletUseCaseTest {

    @Mock
    private CreateWalletUseCase createWalletUseCase;
    @Mock
    private DeleteWalletUseCase deleteWalletUseCase;
    @Mock
    private QueryWalletUseCase queryWalletUseCase;
    @Mock
    private UpdateWalletUseCase updateWalletUseCase;

    private WalletUseCase walletUseCase;

    @BeforeEach
    void setUp() {
        walletUseCase = new WalletUseCase(
                createWalletUseCase,
                deleteWalletUseCase,
                queryWalletUseCase,
                updateWalletUseCase);
    }

    @Test
    void createWalletDelegatesToCreateUseCase() {
        WalletRequestDTO request = new WalletRequestDTO("test-passphrase-bip39".toCharArray(), "TestWallet", null);
        WalletResponseDTO expected = response("TESTWALLET");
        when(createWalletUseCase.createWallet(request, 1L)).thenReturn(expected);

        WalletResponseDTO result = walletUseCase.createWallet(request, 1L);

        assertSame(expected, result);
        verify(createWalletUseCase).createWallet(request, 1L);
    }

    @Test
    void deleteWalletDelegatesToDeleteUseCase() {
        WalletRequestDTO request = new WalletRequestDTO("test-passphrase-bip39".toCharArray(), "TestWallet", null);

        walletUseCase.deleteWallet(request, 1L);

        verify(deleteWalletUseCase).deleteWallet(request, 1L);
    }

    @Test
    void getAllWalletsDelegatesToQueryUseCase() {
        List<WalletResponseDTO> expected = List.of(response("MAIN"));
        when(queryWalletUseCase.getAllWallets(1L)).thenReturn(expected);

        List<WalletResponseDTO> result = walletUseCase.getAllWallets(1L);

        assertSame(expected, result);
        verify(queryWalletUseCase).getAllWallets(1L);
    }

    @Test
    void getWalletByNameDelegatesToQueryUseCase() {
        WalletResponseDTO expected = response("MAIN");
        when(queryWalletUseCase.getWalletByName("Main", 1L)).thenReturn(expected);

        WalletResponseDTO result = walletUseCase.getWalletByName("Main", 1L);

        assertSame(expected, result);
        verify(queryWalletUseCase).getWalletByName("Main", 1L);
    }

    @Test
    void updateWalletDelegatesToUpdateUseCase() {
        WalletUpdateDTO request = new WalletUpdateDTO("test-passphrase-bip39".toCharArray(), "TestWallet", "UpdatedWallet", null);

        walletUseCase.updateWallet(request, 1L);

        verify(updateWalletUseCase).updateWallet(request, 1L);
    }

    private WalletResponseDTO response(String name) {
        return new WalletResponseDTO(
                1L,
                name,
                LocalDateTime.now(),
                LocalDateTime.now(),
                true,
                null,
                "bc1qwallet",
                null,
                "KEROSENE",
                false,
                "BRONZE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("0.0090"),
                new BigDecimal("0.0090"));
    }
}
