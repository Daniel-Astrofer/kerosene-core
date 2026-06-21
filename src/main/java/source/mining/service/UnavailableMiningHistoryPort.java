package source.mining.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class UnavailableMiningHistoryPort implements MiningHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(UnavailableMiningHistoryPort.class);

    @Override
    public void record(MiningHistoryRecord record) {
        log.warn("Mining history persistence is not configured. Skipping record type={}",
                record != null ? record.transactionType() : "unknown");
    }
}
