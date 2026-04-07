package source.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import source.common.service.AddressDerivationService;
import source.transactions.infra.BlockchainClient;
import source.wallet.model.WalletEntity;

/**
 * Agente 2 (Ledger): Recovery service for cold storage and xpub indexing.
 * Scans the blockchain to recover derived address state independently of Redis.
 */
@Service
public class XpubRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(XpubRecoveryService.class);
    private static final int GAP_LIMIT = 20; // BIP44/84 standard gap limit

    private final BlockchainClient blockchainClient;
    private final AddressDerivationService derivationService;
    private final StringRedisTemplate redisTemplate;

    public XpubRecoveryService(BlockchainClient blockchainClient,
                               AddressDerivationService derivationService,
                               StringRedisTemplate redisTemplate) {
        this.blockchainClient = blockchainClient;
        this.derivationService = derivationService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Scans derived addresses for a wallet to find the last used index.
     * Use this if Redis data is lost or during system audit.
     */
    public int recoverLastUsedIndex(WalletEntity wallet) {
        return recoverLastUsedIndex(wallet, GAP_LIMIT);
    }

    /**
     * Scans derived addresses for a wallet to find the last used index.
     * Allows custom maxGapLimit for DeepScans manually triggered by support.
     */
    public int recoverLastUsedIndex(WalletEntity wallet, int maxGapLimit) {
        String xpub = wallet.getXpub();
        if (xpub == null || xpub.isBlank()) {
            return 0;
        }

        boolean isDeepScan = maxGapLimit > GAP_LIMIT;
        String lockKey = "lock:global_deepscan";

        if (isDeepScan) {
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", java.time.Duration.ofMinutes(15));
            if (Boolean.FALSE.equals(lockAcquired)) {
                log.warn("[DeepScan] Rejected request for wallet {}. Another DeepScan is already in progress.", wallet.getId());
                throw new IllegalStateException("DEEPSCAN_LOCKED: A global DeepScan is already running. Please try again later.");
            }
        }

        try {
            log.info("[Recovery] Starting scan for wallet {} (xpub: {}...) with GapLimit={}",
                     wallet.getId(), xpub.substring(0, 10), maxGapLimit);

            int lastUsedIndex = -1;
            int currentGap = 0;
            int index = 0;

            while (currentGap < maxGapLimit) {
                String address = derivationService.deriveAddressFromXpub(xpub, index);

                // Check if address has any transaction history
                com.fasterxml.jackson.databind.JsonNode txs = blockchainClient.getAddressTransactions(address);

                if (txs != null && txs.isArray() && txs.size() > 0) {
                    lastUsedIndex = index;
                    currentGap = 0; // Reset gap since we found activity
                    log.debug("[Recovery] Activity found at index {} (address: {})", index, address);
                } else {
                    currentGap++;
                }
                index++;
            }

            int recoveredIndex = lastUsedIndex + 1;

            // Safety Lock (Agente 2):
            // If no activity was found at all (lastUsedIndex == -1) and we reached the gap limit,
            // it's highly suspicious. We should not blindly reset to 0 if the wallet
            // was supposed to be in use.
            if (lastUsedIndex == -1) {
                log.error("[CRITICAL] Security Lock: Recovery failed for wallet {}. " +
                          "Gap limit reached with ZERO activity. Manual intervention required.", wallet.getId());
                // Mark wallet for manual audit in a real system (attribute update)
                throw new RuntimeException("XPub Recovery Lock Triggered: No blockchain activity found. " +
                                           "Refusing to reset index to 0 to prevent collisions.");
            }

            log.info("[Recovery] Scan complete for wallet {}. Last used index: {}. Next index: {}",
                    wallet.getId(), lastUsedIndex, recoveredIndex);

            // Sync back to Redis to restore operational state
            String indexKey = "xpub_index:" + wallet.getId();
            redisTemplate.opsForValue().set(indexKey, String.valueOf(recoveredIndex));

            return recoveredIndex;
        } finally {
            if (isDeepScan) {
                redisTemplate.delete(lockKey);
            }
        }
    }
}
