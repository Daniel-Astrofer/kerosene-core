package source.bitcoinaccounts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class BitcoinAccountsRetentionService {

    private static final Logger log = LoggerFactory.getLogger(BitcoinAccountsRetentionService.class);

    private final ReceivingRequestService receivingRequestService;
    private final BitcoinTaxEventService taxEventService;
    private final long readableRetentionHours;

    public BitcoinAccountsRetentionService(
            ReceivingRequestService receivingRequestService,
            BitcoinTaxEventService taxEventService,
            @Value("${bitcoin-accounts.readable-retention-hours:24}") long readableRetentionHours) {
        this.receivingRequestService = receivingRequestService;
        this.taxEventService = taxEventService;
        this.readableRetentionHours = Math.max(1, readableRetentionHours);
    }

    @Scheduled(fixedDelayString = "${bitcoin-accounts.retention.fixed-delay-ms:3600000}")
    @Transactional
    public void enforceRetention() {
        LocalDateTime cutoff = LocalDateTime.now();
        receivingRequestService.expireDueRequests();
        receivingRequestService.purgeReadableReceiveData(cutoff);
        taxEventService.purgeReadableEventsOlderThan(cutoff);
        log.info("[BitcoinAccountsRetention] Enforced {}h readable transaction retention.", readableRetentionHours);
    }
}
