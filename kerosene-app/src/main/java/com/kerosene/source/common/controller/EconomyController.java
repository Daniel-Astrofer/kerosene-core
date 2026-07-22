package source.common.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.common.service.BtcPriceQuoteBuilder;
import source.common.service.TickerService;

import java.util.HashMap;
import java.util.Map;

/**
 * Platform economy and market status endpoints.
 *
 * Financial execution, balances, wallets and transaction ownership remain inside KFE.
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
        Map<String, Object> data = BtcPriceQuoteBuilder.build(tickerService);
        return ResponseEntity.ok(ApiResponse.success(
                "Current BTC market prices retrieved.",
                data));
    }
}
