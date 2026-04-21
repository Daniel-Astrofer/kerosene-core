package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.model.ProcessedTransactionEntity;
import source.transactions.repository.ProcessedTransactionRepository;

@Service
public class ProcessedTransactionService {

    private static final Logger log = LoggerFactory.getLogger(ProcessedTransactionService.class);

    private final ProcessedTransactionRepository processedTransactionRepository;

    public ProcessedTransactionService(ProcessedTransactionRepository processedTransactionRepository) {
        this.processedTransactionRepository = processedTransactionRepository;
    }

    @Transactional
    public boolean processOnce(String txid, String source, Runnable processor) {
        if (txid == null || txid.isBlank()) {
            throw new IllegalArgumentException("txid is required for idempotent processing");
        }

        try {
            processedTransactionRepository.saveAndFlush(new ProcessedTransactionEntity(txid, source));
        } catch (DataIntegrityViolationException duplicate) {
            log.info("[ProcessedTx] Transaction {} already processed. Skipping duplicate credit.", txid);
            return false;
        }

        processor.run();
        return true;
    }
}
