package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.model.ExternalProviderOutboxEntity;

import java.net.InetAddress;
import java.util.List;

@Service
public class ExternalProviderOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(ExternalProviderOutboxWorker.class);

    private final ExternalProviderOutboxService outboxService;
    private final ExternalProviderOutboxProcessor processor;
    private final FinancialOperationsMetrics metrics;
    private final String workerId;

    public ExternalProviderOutboxWorker(
            ExternalProviderOutboxService outboxService,
            ExternalProviderOutboxProcessor processor,
            FinancialOperationsMetrics metrics,
            @Value("${transactions.provider-outbox.worker-id:}") String configuredWorkerId) {
        this.outboxService = outboxService;
        this.processor = processor;
        this.metrics = metrics;
        this.workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
                ? defaultWorkerId()
                : configuredWorkerId;
    }

    @Scheduled(
            fixedDelayString = "${transactions.provider-outbox.fixed-delay-ms:30000}",
            initialDelayString = "${transactions.provider-outbox.initial-delay-ms:60000}")
    public void processDue() {
        ExternalProviderOutboxService.ProviderOutboxBacklogSnapshot snapshot = outboxService.backlogSnapshot();
        metrics.increment("external_provider_outbox_backlog_seen", String.valueOf(snapshot.backlog() > 0));

        List<ExternalProviderOutboxEntity> claimed = outboxService.claimDue(workerId);
        for (ExternalProviderOutboxEntity outbox : claimed) {
            try {
                processor.process(outbox.getId());
            } catch (RuntimeException exception) {
                log.warn("[ExternalProviderOutbox] workerId={} outboxId={} failed: {}",
                        workerId,
                        outbox.getId(),
                        exception.getMessage());
                metrics.increment("external_provider_outbox_worker", "failed", outbox.getOperationType());
            }
        }
    }

    private String defaultWorkerId() {
        try {
            return "external-provider-outbox-" + InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "external-provider-outbox-worker";
        }
    }
}
