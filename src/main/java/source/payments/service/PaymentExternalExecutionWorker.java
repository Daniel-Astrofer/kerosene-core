package source.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;

@Service
public class PaymentExternalExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentExternalExecutionWorker.class);
    private static final String WORKER_ID = "payment-execution-" + ManagementFactory.getRuntimeMXBean().getName();

    private final PaymentExecutionOutboxService outboxService;
    private final PaymentExternalExecutionProcessor processor;

    public PaymentExternalExecutionWorker(
            PaymentExecutionOutboxService outboxService,
            PaymentExternalExecutionProcessor processor) {
        this.outboxService = outboxService;
        this.processor = processor;
    }

    @Scheduled(
            fixedDelayString = "${payments.execution.worker.fixed-delay-ms:15000}",
            initialDelayString = "${payments.execution.worker.initial-delay-ms:60000}")
    public void processDueExecutions() {
        try {
            outboxService.claimDue(WORKER_ID).forEach(outbox -> {
                try {
                    processor.process(outbox.getId());
                } catch (RuntimeException exception) {
                    log.error("[PaymentExecution] Processing failed for outboxId={}: {}",
                            outbox.getId(),
                            exception.getMessage());
                }
            });
        } catch (DataAccessException exception) {
            log.warn("[PaymentExecution] Outbox storage unavailable. Worker will retry later: {}",
                    exception.getMostSpecificCause().getMessage());
        }
    }
}
