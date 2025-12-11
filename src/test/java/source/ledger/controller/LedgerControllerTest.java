package source.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import source.ledger.dto.LedgerDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.orchestrator.TransactionContract;
import source.ledger.service.LedgerService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;
import source.auth.model.entity.UserDataBase;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(LedgerController.class)
@AutoConfigureMockMvc(addFilters = false)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LedgerController Tests")
class LedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LedgerService ledgerService;

    @MockBean
    private WalletService walletService;

    @MockBean
    private TransactionContract transaction;

    private TransactionDTO transactionDTO;
    private LedgerDTO ledgerDTO;
    private WalletEntity wallet;
    private LedgerEntity ledger;
    private UserDataBase user;

    @BeforeEach
    void setUp() {
        user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("testuser");

        wallet = new WalletEntity();
        wallet.setId(1L);
        wallet.setName("TestWallet");
        wallet.setUser(user);

        ledger = new LedgerEntity(wallet, "Initial context");
        ledger.setId(1);
        ledger.setBalance(new BigDecimal("100.00"));

        transactionDTO = new TransactionDTO();
        transactionDTO.setSender("sender");
        transactionDTO.setReceiver("receiver");
        transactionDTO.setAmount(new BigDecimal("50.00"));
        transactionDTO.setContext("Test transaction");

        ledgerDTO = new LedgerDTO();
        ledgerDTO.setId(1);
        ledgerDTO.setWalletId(1L);
        ledgerDTO.setWalletName("TestWallet");
        ledgerDTO.setBalance(new BigDecimal("100.00"));
        ledgerDTO.setNonce(0);
        ledgerDTO.setLastHash("test-hash");
        ledgerDTO.setContext("Initial context");
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should process transaction successfully")
    void shouldProcessTransactionSuccessfully() throws Exception {
        doNothing().when(transaction).processTransaction(any(TransactionDTO.class));

        mockMvc.perform(post("/ledger/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transactionDTO)))
                .andExpect(status().isAccepted());

        verify(transaction).processTransaction(any(TransactionDTO.class));
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should get all ledgers successfully")
    void shouldGetAllLedgersSuccessfully() throws Exception {
        List<LedgerEntity> ledgers = Arrays.asList(ledger);
        List<LedgerDTO> ledgerDTOs = Arrays.asList(ledgerDTO);

        when(ledgerService.findByUserId(1L)).thenReturn(ledgers);
        when(ledgerService.toDTOList(ledgers)).thenReturn(ledgerDTOs);

        mockMvc.perform(get("/ledger/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].walletName").value("TestWallet"))
                .andExpect(jsonPath("$[0].balance").value(100.00));

        verify(ledgerService).findByUserId(1L);
        verify(ledgerService).toDTOList(ledgers);
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should get ledger by wallet name successfully")
    void shouldGetLedgerByWalletNameSuccessfully() throws Exception {
        when(walletService.findByName("TestWallet")).thenReturn(wallet);
        doNothing().when(ledgerService).validateWalletOwnership(wallet, 1L);
        when(ledgerService.findByWalletId(1L)).thenReturn(ledger);
        when(ledgerService.toDTO(ledger)).thenReturn(ledgerDTO);

        mockMvc.perform(get("/ledger/find")
                .param("walletName", "TestWallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.walletName").value("TestWallet"));

        verify(walletService).findByName("TestWallet");
        verify(ledgerService).validateWalletOwnership(wallet, 1L);
        verify(ledgerService).findByWalletId(1L);
        verify(ledgerService).toDTO(ledger);
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should get balance successfully")
    void shouldGetBalanceSuccessfully() throws Exception {
        BigDecimal expectedBalance = new BigDecimal("100.00");

        when(walletService.findByName("TestWallet")).thenReturn(wallet);
        doNothing().when(ledgerService).validateWalletOwnership(wallet, 1L);
        when(ledgerService.getBalance(1L)).thenReturn(expectedBalance);

        mockMvc.perform(get("/ledger/balance")
                .param("walletName", "TestWallet"))
                .andExpect(status().isOk())
                .andExpect(content().string("100.00"));

        verify(walletService).findByName("TestWallet");
        verify(ledgerService).validateWalletOwnership(wallet, 1L);
        verify(ledgerService).getBalance(1L);
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should delete ledger successfully")
    void shouldDeleteLedgerSuccessfully() throws Exception {
        when(walletService.findByName("TestWallet")).thenReturn(wallet);
        doNothing().when(ledgerService).validateWalletOwnership(wallet, 1L);
        doNothing().when(ledgerService).deleteLedger(1L);

        mockMvc.perform(delete("/ledger/delete")
                .param("walletName", "TestWallet"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ledger deleted successfully"));

        verify(walletService).findByName("TestWallet");
        verify(ledgerService).validateWalletOwnership(wallet, 1L);
        verify(ledgerService).deleteLedger(1L);
    }
}
