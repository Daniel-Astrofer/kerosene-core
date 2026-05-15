package source.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;
import source.common.service.AddressDerivationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OnrampService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OnrampService.class);

    private final WalletService walletService;
    private final AddressDerivationService addressDerivationService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Value("${onramp.moonpay.url:https://buy.moonpay.com}")
    private String moonpayBaseUrl;

    @Value("${onramp.banxa.url:https://checkout.banxa.com}")
    private String banxaBaseUrl;

    @Value("${onramp.bipa.url:https://bipa.app/buy/btc}")
    private String bipaBaseUrl;

    public OnrampService(WalletService walletService,
                          AddressDerivationService addressDerivationService,
                          org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.walletService = walletService;
        this.addressDerivationService = addressDerivationService;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, String> generateOnrampUrls(Long userId) {
        // Agente 4: Rate Limiting on XPUB derivation (Go-Live Requirement)
        // Prevents bot-induced Postgres bloat and index-skipping attacks.
        String rateLimitKey = "rl:onramp:" + userId;
        String currentCount = redisTemplate.opsForValue().get(rateLimitKey);
        if (currentCount != null && Integer.parseInt(currentCount) >= 5) {
            log.warn("[RateLimit] Too many address generations for user {}. Blocking.", userId);
            throw new RuntimeException("ADDRESS_GENERATION_LIMIT_EXCEEDED: Max 5 addresses per hour.");
        }

        List<WalletEntity> wallets = walletService.findByUserId(userId);
        if (wallets == null || wallets.isEmpty()) {
            throw new IllegalStateException("User has no wallet address to receive funds.");
        }

        // Use the first wallet
        WalletEntity wallet = wallets.get(0);
        String address = wallet.getDepositAddress();

        // If wallet has xpub, derive a unique address for this onramp session
        if (wallet.getXpub() != null && !wallet.getXpub().isBlank()) {
            try {
                // Agente 2: Get next index from Postgres instead of Redis
                // to prevent index reset/collisions after Redis restart.
                int index = walletService.incrementLastDerivedIndex(wallet.getId());

                // Derive address
                address = addressDerivationService.deriveAddressFromXpub(wallet.getXpub(), index);

                // Agente 4: Increment rate limit bucket
                redisTemplate.opsForValue().increment(rateLimitKey);
                redisTemplate.expire(rateLimitKey, java.time.Duration.ofHours(1));

                // Register this address to be watched by the BlockchainMonitorService
                // Value stores the wallet ID and user ID for attribution
                String watchKey = "address_watch:" + address;
                String watchData = String.format("%d:%d", wallet.getId(), userId);
                // Reduce TTL to 1 day as requested
                redisTemplate.opsForValue().set(watchKey, watchData, java.time.Duration.ofDays(1));

                org.slf4j.LoggerFactory.getLogger(OnrampService.class)
                    .info("[Onramp] Derived unique address {} from xpub for wallet {} (Index: {})",
                        address, wallet.getId(), index);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(OnrampService.class)
                    .warn("[Onramp] Failed to derive address from xpub, falling back to static address: {}", e.getMessage());
            }
        }

        Map<String, String> urls = new HashMap<>();
        urls.put("moonpay", String.format("%s?currencyCode=btc&walletAddress=%s",
                moonpayBaseUrl, address));
        urls.put("banxa", String.format("%s?coinType=BTC&fiatType=USD&walletAddress=%s",
                banxaBaseUrl, address));
        urls.put("bipa", String.format("%s?address=%s",
                bipaBaseUrl, address));

        return urls;
    }
}
