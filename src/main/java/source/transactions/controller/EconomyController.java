package source.transactions.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;

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

    public EconomyController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
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
}
