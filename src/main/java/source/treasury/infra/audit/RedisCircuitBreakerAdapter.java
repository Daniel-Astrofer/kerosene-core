package source.treasury.infra.audit;

import org.springframework.stereotype.Component;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.treasury.application.port.out.CircuitBreakerPort;

@Component
public class RedisCircuitBreakerAdapter implements CircuitBreakerPort {

    public static final String CIRCUIT_BREAKER_HALT_DEPOSITS = "circuit_breaker:halt_deposits";
    public static final String CIRCUIT_BREAKER_HALT_WITHDRAWALS = "circuit_breaker:halt_withdrawals";

    private final RedisServicer redisService;

    public RedisCircuitBreakerAdapter(RedisServicer redisService) {
        this.redisService = redisService;
    }

    @Override
    public void haltDeposits() {
        redisService.setValue(CIRCUIT_BREAKER_HALT_DEPOSITS, "TRUE", 0);
    }

    @Override
    public void haltWithdrawals() {
        redisService.setValue(CIRCUIT_BREAKER_HALT_WITHDRAWALS, "TRUE", 0);
    }

    @Override
    public boolean isDepositsHalted() {
        return "TRUE".equals(redisService.getValue(CIRCUIT_BREAKER_HALT_DEPOSITS));
    }

    @Override
    public boolean isWithdrawalsHalted() {
        return "TRUE".equals(redisService.getValue(CIRCUIT_BREAKER_HALT_WITHDRAWALS));
    }

    @Override
    public void reset() {
        redisService.deleteValue(CIRCUIT_BREAKER_HALT_DEPOSITS);
        redisService.deleteValue(CIRCUIT_BREAKER_HALT_WITHDRAWALS);
    }
}
