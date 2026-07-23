package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Former HashiCorp / Java-vault shard heartbeat beacon.
 *
 * Ops AES keys load from {@code api.secret.aes.secret}; treasury custody is
 * vault-mesh. The legacy vault.enabled heartbeat path is retired.
 */
@Service
public class SovereigntyHeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(SovereigntyHeartbeatService.class);

    public SovereigntyHeartbeatService() {
        logger.info("[Heartbeat] Legacy HashiCorp vault heartbeat disabled (vault-mesh cutover).");
    }
}
