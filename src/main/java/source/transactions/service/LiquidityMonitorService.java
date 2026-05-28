package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.common.infra.RedisAvailabilityGuard;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.LightningClient;

/**
 * LiquidityMonitorService
 * Monitors hot wallet balances and lightning channel states.
 * Implements a dynamic fee calculation and a withdrawal circuit breaker.
 */
@Service
public class LiquidityMonitorService {

    private static final Logger log = LoggerFactory.getLogger(LiquidityMonitorService.class);

    private final StringRedisTemplate redisTemplate;
    private final BlockchainClient blockchainClient;
    private final LightningClient lightningClient;
    private final FeeCalculator feeCalculator;
    private final RedisAvailabilityGuard redisAvailabilityGuard;

    // Security Thresholds
    @Value("${liquidity.min.onchain.reserve:5000000}")
    private long minOnchainReserve; // 0.05 BTC

    @Value("${liquidity.target.channel.ratio:0.8}")
    private double targetChannelRatio; // 80% Local Balance = Loop Out

    // Redis Keys
    private static final String STATUS_WITHDRAWALS = "system:status:withdrawals";
    private static final String STATUS_DEPOSITS = "system:status:deposits";
    private static final String CURRENT_WITHDRAWAL_FEE = "economy:current_withdrawal_fee";
    private static final String CHANNEL_HEALTH_SCORE = "system:health:lightning";
    private static final String LATENCY_EMA = "system:health:latency_ema";
    private static final String UPTIME_EMA = "system:health:uptime_ema";

    // Thresholds
    private static final double MIN_UPTIME_THRESHOLD = 0.999;
    // Calibration for Stealth Transport (Fake-TCP overhead)
    private static final long MAX_LATENCY_THRESHOLD_MS = 1500;
    private static final double EMA_ALPHA = 0.15; // More history, less reactive to jitter

    public LiquidityMonitorService(StringRedisTemplate redisTemplate,
                                   BlockchainClient blockchainClient,
                                   LightningClient lightningClient,
                                   FeeCalculator feeCalculator,
                                   RedisAvailabilityGuard redisAvailabilityGuard) {
        this.redisTemplate = redisTemplate;
        this.blockchainClient = blockchainClient;
        this.lightningClient = lightningClient;
        this.feeCalculator = feeCalculator;
        this.redisAvailabilityGuard = redisAvailabilityGuard;
    }

    /**
     * Executes every 10 minutes to maintain global liquidity health.
     */
    @Scheduled(fixedRate = 600000)
    public void checkLiquidityHealth() {
        if (!redisAvailabilityGuard.isAvailable()) {
            log.debug("[LiquidityMonitor] Skipping cycle because Redis is unavailable: {}",
                    redisAvailabilityGuard.describeLastFailure());
            return;
        }

        long onchainBalance;
        try {
            onchainBalance = blockchainClient.getHotWalletBalance();
        } catch (RuntimeException ex) {
            log.warn("[LiquidityMonitor] Skipping cycle because the on-chain wallet is unavailable: {}",
                    rootMessage(ex));
            redisTemplate.opsForValue().set(STATUS_WITHDRAWALS, "DISABLED_WALLET_UNAVAILABLE");
            return;
        }

        LightningBalances lightningBalances = readLightningBalances();
        if (lightningBalances == null) {
            applyOnchainCircuitBreaker(onchainBalance);
            markLightningUnavailable();
            return;
        }
        long localChannelBalance = lightningBalances.localChannelBalance();
        long remoteChannelBalance = lightningBalances.remoteChannelBalance();

        // Using fast confirmation tier for dynamic fee calculation
        BlockchainClient.FeeRates fees = blockchainClient.estimateSmartFee(1, 6, 24);
        long currentMempoolFee = fees.fastSatPerVByte();

        log.info("[LiquidityMonitor] Hydra Status Update:");
        log.info(" - L1 On-chain: {} sats", onchainBalance);
        log.info(" - L2 Local (Outbound): {} sats", localChannelBalance);
        log.info(" - L2 Remote (Inbound): {} sats", remoteChannelBalance);

        // 1. Rebalancing Alert (Loop Out)
        // If outbound capacity is too high compared to total, we need more on-chain liquidity (Loop Out)
        long totalCapacity = localChannelBalance + remoteChannelBalance;
        if (totalCapacity > 0 && localChannelBalance > totalCapacity * targetChannelRatio) {
            triggerLoopOutAlert(localChannelBalance, currentMempoolFee);
        }

        // 2. On-chain Circuit Breaker
        // Disables withdrawals if the hot wallet is too low to fulfill outgoing txs
        applyOnchainCircuitBreaker(onchainBalance);

        // 3. Channel Health Score (Agente 4)
        checkChannelHealth();

        // 4. Dynamic Fee Estimation for Frontend
        calculateAndStoreDynamicFees(currentMempoolFee);
    }

    private LightningBalances readLightningBalances() {
        try {
            return new LightningBalances(
                    lightningClient.getLocalBalance(),
                    lightningClient.getRemoteBalance());
        } catch (RuntimeException ex) {
            log.warn("[LiquidityMonitor] Lightning node unavailable; disabling new Lightning deposits until recovery: {}",
                    rootMessage(ex));
            return null;
        }
    }

    private void applyOnchainCircuitBreaker(long onchainBalance) {
        if (onchainBalance < minOnchainReserve) {
            log.error("[LiquidityMonitor] CRITICAL: Low On-chain liquidity! BTC Withdrawals disabled.");
            redisTemplate.opsForValue().set(STATUS_WITHDRAWALS, "DISABLED_LOW_LIQUIDITY");
        } else {
            redisTemplate.opsForValue().set(STATUS_WITHDRAWALS, "ENABLED");
        }
    }

    private void markLightningUnavailable() {
        redisTemplate.opsForValue().set(STATUS_DEPOSITS, "DISABLED_UNHEALTHY_NODE");
        redisTemplate.opsForValue().set(CHANNEL_HEALTH_SCORE, "CRITICAL");
    }

    private void checkChannelHealth() {
        double currentUptime;
        long currentLatency;
        try {
            currentUptime = lightningClient.getNodeUptime();
            currentLatency = lightningClient.getLspLatency();
        } catch (RuntimeException ex) {
            log.warn("[LiquidityMonitor] Lightning health check unavailable: {}", rootMessage(ex));
            markLightningUnavailable();
            return;
        }

        // Agente 4: Filter binary noise with Exponential Moving Average (EMA)
        String oldUptimeStr = redisTemplate.opsForValue().get(UPTIME_EMA);
        String oldLatencyStr = redisTemplate.opsForValue().get(LATENCY_EMA);

        double avgUptime = oldUptimeStr != null ? Double.parseDouble(oldUptimeStr) : currentUptime;
        double avgLatency = oldLatencyStr != null ? Double.parseDouble(oldLatencyStr) : currentLatency;

        // Update averages: EMA = EMA * alpha + current * (1 - alpha)
        avgUptime = (avgUptime * EMA_ALPHA) + (currentUptime * (1.0 - EMA_ALPHA));
        avgLatency = (avgLatency * EMA_ALPHA) + (currentLatency * (1.0 - EMA_ALPHA));

        // Store for next cycle
        redisTemplate.opsForValue().set(UPTIME_EMA, String.valueOf(avgUptime));
        redisTemplate.opsForValue().set(LATENCY_EMA, String.valueOf(avgLatency));

        log.info("[LiquidityMonitor] Health Trends: Uptime {} | Latency {}ms",
               String.format("%.4f", avgUptime), Math.round(avgLatency));

        // Thresholding based on averages (trend)
        boolean isHealthyTrend = avgUptime >= MIN_UPTIME_THRESHOLD && avgLatency <= MAX_LATENCY_THRESHOLD_MS;

        if (!isHealthyTrend) {
            log.warn("[LiquidityMonitor] UNHEALTHY Node trend detected! Interrupting new deposits.");
            redisTemplate.opsForValue().set(STATUS_DEPOSITS, "DISABLED_UNHEALTHY_NODE");
            redisTemplate.opsForValue().set(CHANNEL_HEALTH_SCORE, "CRITICAL");
        } else {
            redisTemplate.opsForValue().set(STATUS_DEPOSITS, "ENABLED");
            redisTemplate.opsForValue().set(CHANNEL_HEALTH_SCORE, "HEALTHY");
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message != null && !message.isBlank() ? message : cursor.getClass().getSimpleName();
    }

    private record LightningBalances(long localChannelBalance, long remoteChannelBalance) {
    }

    /**
     * Executes an automated Loop Out (Swap Out) operation.
     * Moving funds from Lightning channels to On-chain reserve (Agente 4 Go-Live Requirement).
     */
    private void triggerLoopOut(long localSats) {
        String retryKey = "system:loopout:retries";
        String lastFailKey = "system:loopout:last_fail";

        // Agente 4: Backoff check
        String lastFail = redisTemplate.opsForValue().get(lastFailKey);
        if (lastFail != null) {
            long lastFailTs = Long.parseLong(lastFail);
            long waitMs = Math.min(3600000, 300000 * (long)Math.pow(2, getRetryCount()));
            if (System.currentTimeMillis() - lastFailTs < waitMs) {
                log.debug("[LoopOut] Still in backoff window ({}ms remaining). Skipping.",
                          waitMs - (System.currentTimeMillis() - lastFailTs));
                return;
            }
        }

        long swapAmount = localSats / 2;
        log.info("[LoopOut] Dispatching SWAP request for {} sats (Attempt {})...",
                 swapAmount, getRetryCount() + 1);

        try {
            // Simulated Boltz/PeerSwap API call
            boolean swapInitiated = false; // Simulated failure/offline (Agente 4)

            if (swapInitiated) {
                log.info("[LoopOut] Swap SUCCESS.");
                redisTemplate.delete(retryKey);
                redisTemplate.delete(lastFailKey);
            } else {
                handleLoopOutFailure(swapAmount);
            }
        } catch (Exception e) {
            handleLoopOutFailure(swapAmount);
        }
    }

    private void handleLoopOutFailure(long amount) {
        Long retries = redisTemplate.opsForValue().increment("system:loopout:retries");
        redisTemplate.opsForValue().set("system:loopout:last_fail", String.valueOf(System.currentTimeMillis()));

        log.error("[LoopOut] FAILED to initiate swap. Retry Count: {}. Entering exponential backoff.", retries);

        if (retries != null && retries >= 3) {
            log.warn("[LoopOut] MAX RETRIES EXCEEDED. Entering DEGRADED_LIQUIDITY state.");
            redisTemplate.opsForValue().set("system:status:deposits", "DEGRADED_LIQUIDITY");
        }
    }

    private int getRetryCount() {
        String val = redisTemplate.opsForValue().get("system:loopout:retries");
        return val != null ? Integer.parseInt(val) : 0;
    }

    private void calculateAndStoreDynamicFees(long feePerVByte) {
        // We calculate base fee for a standard transaction (amount irrelevant for base spread)
        long finalFee = feeCalculator.calculateBaseFee(feePerVByte);

        // Save in Redis for Frontend consumption
        redisTemplate.opsForValue().set(CURRENT_WITHDRAWAL_FEE, String.valueOf(finalFee));
        log.debug("[LiquidityMonitor] Updated dynamic withdrawal base fee: {} sats", finalFee);
    }

    private void triggerLoopOutAlert(long amountSats, long currentFee) {
        // Dispatches alerts via logs/telemetry
        log.warn("[LiquidityMonitor] L2 Channel saturated ({} sats). Loop Out recommended at {} sats/vB",
                amountSats, currentFee);

        // Future implementation: automatic trigger via Boltz or Loop API
    }
}
