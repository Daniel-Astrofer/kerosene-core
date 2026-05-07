package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import source.common.infra.logging.LogSanitizer;

@Component
@ConditionalOnProperty(prefix = "lightning.lnd", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopWatchOnlyAddressImportAdapter implements WatchOnlyAddressImportPort {

    private static final Logger log = LoggerFactory.getLogger(NoopWatchOnlyAddressImportAdapter.class);

    @Override
    public String providerName() {
        return "LOCAL_WATCH_ONLY_DISABLED";
    }

    @Override
    public void importWatchOnlyPublicKey(byte[] publicKey, String expectedAddress) {
        log.warn("[WatchOnlyImport] LND is disabled; addressRef={} was derived but not imported into a node.",
                LogSanitizer.fingerprint(expectedAddress));
    }
}
