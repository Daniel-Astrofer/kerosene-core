package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.model.ProcessedTransactionEntity;
import source.transactions.repository.ProcessedTransactionRepository;

@Service
public class ProcessedTransactionService {

    private static final Logger log = LoggerFactory.getLogger(ProcessedTransactionService.class);

    private final ProcessedTransactionRepository processedTransactionRepository;
    private final FinancialOperationsMetrics financialMetrics;

    public ProcessedTransactionService(
            ProcessedTransactionRepository processedTransactionRepository,
            FinancialOperationsMetrics financialMetrics) {
        this.processedTransactionRepository = processedTransactionRepository;
        this.financialMetrics = financialMetrics;
    }

    @Transactional
    public boolean processOnce(String txid, String source, Runnable processor) {
        if (txid == null || txid.isBlank()) {
            throw new IllegalArgumentException("txid is required for idempotent processing");
        }

        try {
            processedTransactionRepository.saveAndFlush(new ProcessedTransactionEntity(txid, source));
        } catch (DataIntegrityViolationException duplicate) {
            financialMetrics.increment("idempotency_reused", "duplicate", source);
            log.info("[ProcessedTx] Transaction txRef={} already processed. Skipping duplicate credit.",
                    LogSanitizer.fingerprint(txid));
            return false;
        }

        processor.run();
        return true;
    }
}
