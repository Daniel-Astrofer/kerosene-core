package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.auth.application.orchestrator.signup.SignupUseCase;
import source.transactions.dto.PaymentLinkDTO;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class OnboardingMonitorService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingMonitorService.class);

    private final RedisTemplate<String, PaymentLinkDTO> redisTemplate;
    private final SignupUseCase signupUseCase;

    private static final int REQUIRED_CONFIRMATIONS = 3;

    public OnboardingMonitorService(RedisTemplate<String, PaymentLinkDTO> redisTemplate,
            SignupUseCase signupUseCase) {
        this.redisTemplate = redisTemplate;
        this.signupUseCase = signupUseCase;
    }

    /**
     * Checks pending onboarding transactions every 1 minute.
     * Looks for payment links that are flagged as "verifying_onboarding"
     */
    @Scheduled(fixedDelay = 60000)
    public void monitorOnboardingConfirmations() {
        Set<String> keys = redisTemplate.keys("payment_link:*");
        if (keys == null || keys.isEmpty())
            return;

        boolean hasPending = false;
        for (String key : keys) {
            PaymentLinkDTO dto = redisTemplate.opsForValue().get(key);
            if (dto != null && "verifying_onboarding".equals(dto.getStatus())) {
                if (!hasPending) {
                    log.info("Checking onboarding transaction confirmations...");
                    hasPending = true;
                }
                checkConfirmations(dto, key);
            }
        }
    }

    private void checkConfirmations(PaymentLinkDTO dto, String redisKey) {
        try {
            if (dto.getTxid() == null)
                return;

            // TODO: Consultar via novo Blockchain Client
            Map<String, Object> txInfo = Map.of("confirmations", 3); // Mocked response

            if (txInfo == null || txInfo.isEmpty()) {
                log.warn("Onboarding tx {} not found on blockchain", dto.getTxid());
                return;
            }

            int confirmations = (int) txInfo.getOrDefault("confirmations", 0);
            log.info("Onboarding link {} has {} confirmations (Target: {})", dto.getId(), confirmations,
                    REQUIRED_CONFIRMATIONS);

            if (confirmations >= REQUIRED_CONFIRMATIONS) {
                // We reached 3 confirmations!
                // Finish registration in Postgres
                log.info("3 confirmations reached for link {}! Finalizing user account for session {}.", dto.getId(),
                        dto.getSessionId());

                signupUseCase.finalizeUserFromRedis(dto.getSessionId(), dto.getTxid(), dto.getAmountBtc());

                dto.setStatus("completed");
                redisTemplate.opsForValue().set(redisKey, dto, 24, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("Failed to check confirmations for onboarding link {}", dto.getId(), e);
        }
    }
}
