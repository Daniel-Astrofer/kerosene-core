package source.wallet.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.wallet.application.port.out.WalletPersistencePort;
import source.wallet.model.WalletEntity;

import java.util.List;

@Service
@ConditionalOnProperty(
        prefix = "wallet.destination-hash",
        name = "backfill-on-startup",
        havingValue = "true",
        matchIfMissing = true)
public class WalletDestinationHashBackfillService {

    private static final Logger log = LoggerFactory.getLogger(WalletDestinationHashBackfillService.class);
    private static final int BATCH_SIZE = 500;

    private final WalletPersistencePort walletPersistencePort;

    public WalletDestinationHashBackfillService(WalletPersistencePort walletPersistencePort) {
        this.walletPersistencePort = walletPersistencePort;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingDestinationHashes() {
        int updated = 0;
        while (true) {
            List<WalletEntity> wallets = walletPersistencePort.findTop500ByDestinationHashIsNullOrderByIdAsc();
            if (wallets.isEmpty()) {
                break;
            }

            for (WalletEntity wallet : wallets) {
                wallet.refreshDestinationHash();
                updated++;
            }
            walletPersistencePort.saveAll(wallets);

            if (wallets.size() < BATCH_SIZE) {
                break;
            }
        }

        if (updated > 0) {
            log.info("[WalletDestinationHash] Backfilled destination hashes for {} wallets.", updated);
        }
    }
}
