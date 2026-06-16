package source.wallet.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import source.common.dto.ApiResponse;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.orchestrator.WalletUseCase;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletUseCase walletUseCase;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WalletController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuth() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("123");
    }

    @Test
    void testCreateWallet() {
        mockAuth();
        WalletRequestDTO req = new WalletRequestDTO("pass".toCharArray(), "name", "seed");
        WalletResponseDTO resDto = mock(WalletResponseDTO.class);
        when(walletUseCase.createWallet(req, 123L)).thenReturn(resDto);

        ResponseEntity<ApiResponse<WalletResponseDTO>> res = controller.create(req, mock(HttpServletRequest.class));
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    void testGetAllWallets() {
        mockAuth();
        when(walletUseCase.getAllWallets(123L)).thenReturn(Collections.emptyList());

        ResponseEntity<ApiResponse<List<WalletResponseDTO>>> res = controller.getAllWallets(mock(HttpServletRequest.class));
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    void testGetWalletByName() {
        mockAuth();
        WalletResponseDTO resDto = mock(WalletResponseDTO.class);
        when(walletUseCase.getWalletByName("test", 123L)).thenReturn(resDto);

        ResponseEntity<ApiResponse<WalletResponseDTO>> res = controller.getWalletByName("test", mock(HttpServletRequest.class));
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    void testUpdateWallet() {
        mockAuth();
        WalletUpdateDTO req = new WalletUpdateDTO("pass".toCharArray(), "old", "new", "seed");

        ResponseEntity<ApiResponse<String>> res = controller.updateWallet(req, mock(HttpServletRequest.class));
        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    void testDeleteWallet() {
        mockAuth();
        WalletRequestDTO req = new WalletRequestDTO("pass".toCharArray(), "name", "seed");

        ResponseEntity<ApiResponse<String>> res = controller.deleteWallets(req, mock(HttpServletRequest.class));
        assertEquals(HttpStatus.OK, res.getStatusCode());
    }
}
