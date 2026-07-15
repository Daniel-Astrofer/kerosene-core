package source.auth.application.orchestrator.signup.infra;

import java.time.Duration;

import org.springframework.stereotype.Component;

import source.auth.application.infra.persistence.redis.contracts.RedisContract;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.dto.SignupState;
import source.auth.dto.UserDTO;

@Component
public class RedisSignupStateStore implements SignupStateStore {

    private final RedisServicer tempUserCache;
    private final RedisContract redis;

    public RedisSignupStateStore(RedisServicer tempUserCache, RedisContract redis) {
        this.tempUserCache = tempUserCache;
        this.redis = redis;
    }

    @Override
    public void createPendingUser(UserDTO dto) {
        tempUserCache.createTempUser(dto);
    }

    @Override
    public UserDTO findPendingUser(UserDTO lookup) {
        return tempUserCache.getFromRedis(lookup);
    }

    @Override
    public void deletePendingUser(UserDTO dto) {
        tempUserCache.deleteFromRedis(dto);
    }

    @Override
    public void saveSignupState(String sessionId, SignupState state, Duration ttl) {
        redis.saveSignupState(sessionId, state, ttl.toMinutes());
    }

    @Override
    public SignupState findSignupState(String sessionId) {
        return redis.findSignupState(sessionId);
    }

    @Override
    public SignupState consumeSignupState(String sessionId) {
        return redis.getdelSignupState(sessionId);
    }

    @Override
    public void deleteSignupState(String sessionId) {
        redis.deleteSignupState(sessionId);
    }
}
