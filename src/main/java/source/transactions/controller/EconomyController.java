package source.transactions.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.common.service.TickerService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for platform economy and status endpoints.
 * Provides real-time withdrawal fees and system circuit breaker status.
 */
@RestController
@RequestMapping("/api/economy")
public class EconomyController {

    private final StringRedisTemplate redisTemplate;
    private final TickerService tickerService;

    public EconomyController(StringRedisTemplate redisTemplate, TickerService tickerService) {
        this.redisTemplate = redisTemplate;
        this.tickerService = tickerService;
    }

    /**
     * Retrieves current platform economic metrics.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEconomyStatus() {
        String fee = redisTemplate.opsForValue().get("economy:current_withdrawal_fee");
        String status = redisTemplate.opsForValue().get("system:status:withdrawals");

        Map<String, Object> data = new HashMap<>();
        data.put("withdrawalFeeSats", fee != null ? Long.parseLong(fee) : 10000L);
        data.put("withdrawalStatus", status != null ? status : "ENABLED");

        return ResponseEntity.ok(ApiResponse.success(
            "Current platform liquidity and economy status retrieved.",
            data));
    }

    @GetMapping("/btc-price")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBtcPrice() {
        BigDecimal btcUsd = tickerService.getPrice("usd");
        BigDecimal btcBrl = tickerService.getPrice("brl");
        BigDecimal usdBrl = BigDecimal.ZERO;

        if (btcUsd.compareTo(BigDecimal.ZERO) > 0) {
            usdBrl = btcBrl.divide(btcUsd, 8, java.math.RoundingMode.HALF_UP);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("btcUsd", btcUsd);
        data.put("btcBrl", btcBrl);
        data.put("usdBrl", usdBrl);

        return ResponseEntity.ok(ApiResponse.success(
            "Current BTC market prices retrieved.",
            data));
    }
}
