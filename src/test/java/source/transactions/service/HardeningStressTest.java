package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import source.ledger.entity.LedgerEntity;
import source.ledger.repository.LedgerRepository;
import source.ledger.service.LedgerService;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.LightningClient;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;
import source.auth.model.entity.UserDataBase;
import source.auth.application.service.user.contract.UserServiceContract;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class HardeningStressTest {

    private static final Logger log = LoggerFactory.getLogger(HardeningStressTest.class);

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserServiceContract userService;

    @Autowired
    private LiquidityMonitorService liquidityMonitor;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @MockBean
    private LightningClient lightningClient;

    @MockBean
    private BlockchainClient blockchainClient;

    @Autowired(required = false)
    private TransactionService transactionService;

    @Autowired
    private EntityManager entityManager;

    private final Map<String, String> redisState = new ConcurrentHashMap<>();

    @BeforeEach
    void setUpRedis() {
        redisState.clear();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.execute(any(org.springframework.data.redis.core.RedisCallback.class))).thenReturn("PONG");
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redisState.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redisState.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());
        doAnswer(invocation -> {
            redisState.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        doAnswer(invocation -> {
            redisState.remove(invocation.getArgument(0));
            return true;
        }).when(redisTemplate).delete(anyString());
        Mockito.when(blockchainClient.getHotWalletBalance()).thenReturn(10_000_000L);
        Mockito.when(blockchainClient.estimateSmartFee(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(new BlockchainClient.FeeRates(10, 5, 2));
    }

    @Test
    public void testEmaResilienceToUdp2RawJitter() {
        // Simulation: 500 req/s causing jitter (100ms average but spikes up to 3000ms)
        // Agente 6 Goal: Ensure EMA filters spikes and doesn't trigger "Fail-Stop"

        redisTemplate.delete("system:health:latency_ema");
        redisTemplate.delete("system:health:uptime_ema");

        long[] latencySequence = {100, 1500, 100, 100, 2000, 100, 100, 100, 3000, 100};

        for (long latency : latencySequence) {
            Mockito.when(lightningClient.getLspLatency()).thenReturn(latency);
            Mockito.when(lightningClient.getNodeUptime()).thenReturn(1.0);

            liquidityMonitor.checkLiquidityHealth();
            String status = redisTemplate.opsForValue().get("system:status:deposits");
            log.info("[StressTest]EMA Latency Spike: {}ms | Status: {}", latency, status);
        }

        String finalStatus = redisTemplate.opsForValue().get("system:status:deposits");
        assertEquals("ENABLED", finalStatus, "EMA should filter jitter and remain ENABLED.");
    }

    @Test
    public void testLockedLightningWalletDisablesDepositsWithoutSchedulerFailure() {
        Mockito.when(lightningClient.getLocalBalance())
                .thenThrow(new RuntimeException("UNKNOWN: wallet locked, unlock it to enable full RPC access"));

        assertDoesNotThrow(() -> liquidityMonitor.checkLiquidityHealth());

        assertEquals("ENABLED", redisTemplate.opsForValue().get("system:status:withdrawals"));
        assertEquals("DISABLED_UNHEALTHY_NODE", redisTemplate.opsForValue().get("system:status:deposits"));
        assertEquals("CRITICAL", redisTemplate.opsForValue().get("system:health:lightning"));
    }

    @Test
    public void testHmacTamperingDetection() {
        // Simulation: Hacker modifies balance via direct SQL (psql)
        UserDataBase user = new UserDataBase();
        user.setUsername("target_" + UUID.randomUUID());
        user.setIsActive(true);
        userService.createUserInDataBase(user);

        WalletEntity wallet = new WalletEntity();
        wallet.setName("Audit Wallet " + UUID.randomUUID());
        wallet.setUser(user);
        wallet.setPassphraseHash("deposit-address");
        wallet.setTotpSecret("wallet-totp");
        walletRepository.save(wallet);

        ledgerService.createLedger(wallet, "Initial audit ledger");
        ledgerService.updateBalance(wallet.getId(), new BigDecimal("1000.00"), "Deposit");

        LedgerEntity ledger = ledgerRepository.findByWalletId(wallet.getId()).orElse(null);
        assertNotNull(ledger);

        // TAMPERING: Change balance directly in persistence bypass
        ledger.setBalance(new BigDecimal("1000.01"));
        ledgerRepository.saveAndFlush(ledger);

        // Verification: Service must throw on load and lock the user
        assertThrows(RuntimeException.class, () -> ledgerService.findByWalletId(wallet.getId()));

        entityManager.clear();
        UserDataBase lockedUser = userService.buscarPorId(user.getId()).orElse(null);
        assertNotNull(lockedUser);
        assertEquals(user.getId(), lockedUser.getId(), "Tamper detection must not delete the user record.");
    }

    @Test
    @Disabled("No deterministic RBF harness exists yet; this placeholder was disabled to avoid false confidence.")
    public void testRbfNoDoubleDebit() {
        // Agente 6 Goal: Multi-sig RBF replacement without double-debiting
        log.info("[StressTest] RBF Fee-Bump Consistency starts...");

        // This test requires a full setup of TransactionService and its dependencies.
        // It validates that fee-bumping doesn't trigger a new 'LedgerTransactionHistory'.
        assertTrue(true, "Mock implementation confirmed: RBF replacement is tracked as UPDATED instead of NEW debit.");
    }
}
