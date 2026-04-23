package source.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import source.auth.application.infra.security.ParanoidSecurityFilter;
import source.auth.application.infra.security.RateLimitFilter;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.ledger.dto.LedgerDTO;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.orchestrator.TransactionContract;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerPaymentRequestService;
import source.ledger.service.LedgerService;
import source.security.SuicideService;
import source.security.application.honeypot.HoneypotInspectionUseCase;
import source.security.HoneypotRequestFilter;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;
import source.auth.model.entity.UserDataBase;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@WebMvcTest(
        controllers = LedgerController.class,
        excludeFilters = {
                @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class),
                @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ParanoidSecurityFilter.class),
                @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = HoneypotRequestFilter.class)
        })
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

    @MockBean
    private SuicideService suicideService;

    @MockBean
    private RedisServicer redisServicer;

    @MockBean
    private LedgerPaymentRequestService paymentRequestService;

    @MockBean
    private LedgerTransactionHistoryRepository historyRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private HoneypotInspectionUseCase honeypotInspectionUseCase;

    private TransactionDTO transactionDTO;
    private LedgerDTO ledgerDTO;
    private WalletEntity wallet;
    private LedgerEntity ledger;
    private UserDataBase user;
    private InternalPaymentRequestDTO paymentRequestDTO;
    private ValueOperations<String, String> valueOperations;

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

        paymentRequestDTO = new InternalPaymentRequestDTO();
        paymentRequestDTO.setId("pr_123");
        paymentRequestDTO.setRequesterUserId(1L);
        paymentRequestDTO.setReceiverWalletId(1L);
        paymentRequestDTO.setReceiverWalletName("TestWallet");
        paymentRequestDTO.setAmount(new BigDecimal("25.00"));
        paymentRequestDTO.setStatus("PENDING");

        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should process transaction successfully")
    void shouldProcessTransactionSuccessfully() throws Exception {
        doNothing().when(transaction).processTransaction(any(TransactionDTO.class));

        mockMvc.perform(post("/ledger/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transactionDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(50.00));

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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].walletName").value("TestWallet"))
                .andExpect(jsonPath("$.data[0].balance").value(100.00));

        verify(ledgerService).findByUserId(1L);
        verify(ledgerService).toDTOList(ledgers);
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should get ledger by wallet name successfully")
    void shouldGetLedgerByWalletNameSuccessfully() throws Exception {
        when(walletService.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(ledgerService.findByWalletId(1L)).thenReturn(ledger);
        when(ledgerService.toDTO(ledger)).thenReturn(ledgerDTO);

        mockMvc.perform(get("/ledger/find")
                .param("walletName", "TestWallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.walletName").value("TestWallet"));

        verify(walletService).findByNameAndUserId("TestWallet", 1L);
        verify(ledgerService).findByWalletId(1L);
        verify(ledgerService).toDTO(ledger);
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should get balance successfully")
    void shouldGetBalanceSuccessfully() throws Exception {
        BigDecimal expectedBalance = new BigDecimal("100.00");

        when(walletService.findByNameAndUserId("TestWallet", 1L)).thenReturn(wallet);
        when(ledgerService.getBalance(1L)).thenReturn(expectedBalance);

        mockMvc.perform(get("/ledger/balance")
                .param("walletName", "TestWallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(100.00));

        verify(walletService).findByNameAndUserId("TestWallet", 1L);
        verify(ledgerService).getBalance(1L);
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("Should create payment request successfully")
    void shouldCreatePaymentRequestSuccessfully() throws Exception {
        when(paymentRequestService.createRequest(1L, new BigDecimal("25.00"), "TestWallet"))
                .thenReturn(paymentRequestDTO);

        mockMvc.perform(post("/ledger/payment-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":25.00,"receiverWalletName":"TestWallet"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("pr_123"))
                .andExpect(jsonPath("$.data.receiverWalletName").value("TestWallet"));

        verify(paymentRequestService).createRequest(1L, new BigDecimal("25.00"), "TestWallet");
    }
}
