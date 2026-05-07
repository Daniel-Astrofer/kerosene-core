package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.ledger.entity.SiphonRequest;

import java.net.InetAddress;
import java.util.List;

@Service
public class TreasuryPayoutWorker {

    private static final Logger log = LoggerFactory.getLogger(TreasuryPayoutWorker.class);

    private final TreasuryPayoutService payoutService;
    private final TreasuryPayoutExecutionProcessor processor;
    private final String workerId;

    public TreasuryPayoutWorker(
            TreasuryPayoutService payoutService,
            TreasuryPayoutExecutionProcessor processor,
            @Value("${treasury.payout.worker-id:}") String configuredWorkerId) {
        this.payoutService = payoutService;
        this.processor = processor;
        this.workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
                ? defaultWorkerId()
                : configuredWorkerId;
    }

    @Scheduled(
            fixedDelayString = "${treasury.payout.fixed-delay-ms:30000}",
            initialDelayString = "${treasury.payout.initial-delay-ms:60000}")
    public void processDue() {
        TreasuryPayoutService.PayoutBacklogSnapshot snapshot = payoutService.backlogSnapshot();
        if (snapshot.backlog() <= 0) {
            return;
        }

        List<SiphonRequest> claimed = payoutService.claimDue(workerId);
        for (SiphonRequest request : claimed) {
            try {
                processor.process(request.getId());
            } catch (RuntimeException exception) {
                log.warn("[TreasuryPayoutWorker] workerId={} requestId={} failed: {}",
                        workerId,
                        request.getId(),
                        exception.getMessage());
            }
        }
    }

    private String defaultWorkerId() {
        try {
            return "treasury-payout-" + InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "treasury-payout-worker";
        }
    }
}
