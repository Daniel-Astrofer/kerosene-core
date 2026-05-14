package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.transactions.model.PendingTransaction;
import source.transactions.repository.PendingTransactionRedisRepository;
import source.wallet.service.WalletService;
import source.ledger.service.LedgerService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import org.springframework.data.redis.core.StringRedisTemplate;

@Service
public class BlockchainMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainMonitorService.class);
    private static final int MIN_CONFIRMATIONS = 1;

    // Issue 2.3: Adaptive interval backoff — doubles on rate-limit, halves on
    // success
    @Value("${blockchain.monitor.interval.min:10000}")
    private long minInterval;
    @Value("${blockchain.monitor.interval.max:120000}")
    private long maxInterval;
    private volatile long currentBackoffMs = 0;

    private final PendingTransactionRedisRepository repository;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final source.notification.service.NotificationService notificationService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;

    public BlockchainMonitorService(PendingTransactionRedisRepository repository,
            WalletService walletService,
            LedgerService ledgerService,
            source.notification.service.NotificationService notificationService,
            LedgerTransactionHistoryRepository historyRepository,
            StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
        this.historyRepository = historyRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Verifica transações pendentes a cada 30 segundos
     */
    @Scheduled(fixedDelay = 30000)
    public void monitorPendingTransactions() {
        // Issue 2.3: Respect adaptive backoff — skip cycle if still cooling down
        if (currentBackoffMs > 0) {
            currentBackoffMs = Math.max(0, currentBackoffMs - 30000);
            log.debug("[BlockchainMonitor] Skipping cycle (backoff cooling down: {}ms remaining)", currentBackoffMs);
            return;
        }

        List<PendingTransaction> pendingTxs = repository.findByStatus("PENDING");
        if (pendingTxs.isEmpty()) {
            return;
        }

        log.info("Checking {} pending transaction(s)", pendingTxs.size());
        boolean rateLimitHit = false;
        for (PendingTransaction tx : pendingTxs) {
            try {
                if (tx.getTxid().startsWith("mock-") || tx.getTxid().length() != 64) {
                    log.warn("Marking INVALID transaction as FAILED: {}", tx.getTxid());
                    tx.setStatus("FAILED");
                    tx.setErrorMessage("Invalid TXID format - Cleanup");
                    repository.save(tx);
                    continue;
                }
                checkTransaction(tx);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("rate") || msg.contains("429") || msg.contains("too many")) {
                    log.warn("[BlockchainMonitor] Rate limited by blockchain API. Backing off.");
                    currentBackoffMs = Math.min(currentBackoffMs == 0 ? minInterval : currentBackoffMs * 2,
                            maxInterval);
                    rateLimitHit = true;
                    break;
                }
                log.error("Error checking transaction {}: {}", tx.getTxid(), e.getMessage());
            }
        }
        // Reduce backoff on fully successful cycles
        if (!rateLimitHit && currentBackoffMs > 0) {
            currentBackoffMs = Math.max(0, currentBackoffMs / 2);
        }
    }

    /**
     * Verifica status de uma transação específica na blockchain
     */
    public void checkTransaction(PendingTransaction tx) {
        try {
            log.info("Checking transaction: {}", tx.getTxid());

            // TODO: Consultar via novo Blockchain Client
            Map<String, Object> txInfo = Map.of("confirmations", 1); // Mocked response

            if (txInfo == null || txInfo.isEmpty()) {
                log.warn("Transaction {} not found on blockchain yet", tx.getTxid());
                return;
            }

            // Extrai confirmações
            Integer confirmations = (Integer) txInfo.getOrDefault("confirmations", 0);

            // NOTIFICAÇÃO DE DEPÓSITO PENDENTE (DETECTADO)
            if (confirmations == 0 && tx.getConfirmations() == null) {
                try {
                    String title = "Depósito Identificado";
                    String body = String.format(
                            "Um depósito de %s BTC está pendente na rede. Aguardando confirmações de segurança.",
                            tx.getAmount().toPlainString());
                    notificationService.notifyUser(tx.getUserId(), title, body);
                } catch (Exception e) {
                    log.error("Erro ao notificar depósito pendente: " + e.getMessage());
                }
            }

            tx.setConfirmations(confirmations);

            // Update history confirmations even if not concluded yet
            try {
                historyRepository.findByBlockchainTxid(tx.getTxid()).ifPresent(h -> {
                    h.setConfirmations(confirmations);
                    historyRepository.save(h);
                });
            } catch (Exception e) {
                log.error("Failed to update history confirmations: " + e.getMessage());
            }

            log.info("Transaction {} has {} confirmation(s)", tx.getTxid(), confirmations);

            // Issue 1.5: Timing-safe status comparison
            if (confirmations >= MIN_CONFIRMATIONS
                    && !timingSafeStatusEquals(tx.getStatus(), "CONFIRMED")) {
                tx.setStatus("CONFIRMED");
                tx.setConfirmedAt(LocalDateTime.now());
                log.info("Transaction {} CONFIRMED", tx.getTxid());

                // Bug 4 Fix: Idempotent processing to prevent Double-Spend
                try {
                    processTransactionConfirmationIdempotent(tx, confirmations);
                } catch (Exception e) {
                    log.error("Failed to process idempotently tx {}: {}", tx.getTxid(), e.getMessage());
                    // Skip saving CONFIRMED status if processing failed so it retries
                    return;
                }
            } // end if CONFIRMED

            repository.save(tx);

        } catch (Exception e) {
            log.error("Error checking transaction {}: {}", tx.getTxid(), e.getMessage());
            if (tx.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                tx.setStatus("FAILED");
                tx.setErrorMessage("Transaction not found after 24 hours");
                repository.save(tx);
            }
        }
    }

    /**
     * Bug 4 Fix: Distributed Idempotency Lock
     * Prevents split-brain or network retries from double-spending on-chain
     * deposits.
     */
    public void processTransactionConfirmationIdempotent(PendingTransaction tx, Integer confirmations) {
        String idempotencyKey = "tx_processed:" + tx.getTxid();
        Boolean stringLocked = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "LOCKED",
                java.time.Duration.ofDays(7));

        if (Boolean.FALSE.equals(stringLocked)) {
            log.info("[Idempotent] Transaction {} was already processed. Skipping.", tx.getTxid());
            return;
        }

        try {
            // 1. Process SENDER (DEBIT) - If address belongs to us
            try {
                WalletEntity senderWallet = walletService.findByPassphraseHash(tx.getFromAddress());
                if (senderWallet != null) {
                    BigDecimal totalDeduction = tx.getAmount().add(
                            BigDecimal.valueOf(tx.getFeeSatoshis()).divide(BigDecimal.valueOf(100_000_000)));

                    ledgerService.updateBalance(
                            senderWallet.getId(),
                            totalDeduction.negate(),
                            "transfer_out: " + tx.getTxid());

                    log.info("Deducted {} BTC from sender wallet {} for tx {}", totalDeduction,
                            senderWallet.getId(), tx.getTxid());

                    // Notify Sender
                    try {
                        String title = "Transferência Confirmada";
                        String body = String.format(
                                "A transferência de %s BTC da carteira '%s' foi confirmada na rede Blockchain.",
                                tx.getAmount().toPlainString(), senderWallet.getName());
                        notificationService.notifyUser(senderWallet.getUser().getId(), title, body);

                        // Update history
                        historyRepository.findByBlockchainTxid(tx.getTxid()).ifPresent(h -> {
                            h.setStatus("CONCLUDED");
                            h.setConfirmations(confirmations);
                            historyRepository.save(h);
                        });
                    } catch (Exception ne) {
                        log.error("Erro ao notificar confirmação de envio: " + ne.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to update sender balance for tx {}: {}", tx.getTxid(), e.getMessage());
                throw e; // throw so lock is deleted
            }

            // 2. Process RECEIVER (CREDIT) - If address belongs to us
            try {
                WalletEntity receiverWallet = walletService.findByPassphraseHash(tx.getToAddress());
                if (receiverWallet != null) {
                    ledgerService.updateBalance(
                            receiverWallet.getId(),
                            tx.getAmount(),
                            "transfer_in: " + tx.getTxid());

                    log.info("Credited {} BTC to receiver wallet {} for tx {}", tx.getAmount(),
                            receiverWallet.getId(), tx.getTxid());

                    // Notify Receiver
                    try {
                        String title = "Depósito Confirmado";
                        String body = String.format("O aporte de %s BTC na carteira '%s' foi confirmado.",
                                tx.getAmount().toPlainString(), receiverWallet.getName());
                        notificationService.notifyUser(receiverWallet.getUser().getId(), title, body);

                        // If history entry doesn't exist for this deposit, create it
                        if (historyRepository.findByBlockchainTxid(tx.getTxid()).isEmpty()) {
                            LedgerTransactionHistory history = new LedgerTransactionHistory();
                            history.setId(UUID.randomUUID());
                            history.setAmount(tx.getAmount());
                            history.setCreatedAt(LocalDateTime.now());
                            history.setContext("On-Chain Deposit: External Bitcoin Deposit");
                            history.setReceiverUserId(receiverWallet.getUser().getId());
                            history.setReceiverIdentifier(receiverWallet.getName());
                            history.setSenderIdentifier(
                                    tx.getFromAddress() != null ? tx.getFromAddress() : "UNKNOWN");
                            history.setBlockchainTxid(tx.getTxid());
                            history.setTransactionType("EXTERNAL_DEPOSIT");
                            history.setStatus("CONCLUDED");
                            history.setConfirmations(confirmations);
                            historyRepository.save(history);
                        } else {
                            // Update existing (unlikely for deposits)
                            historyRepository.findByBlockchainTxid(tx.getTxid()).ifPresent(h -> {
                                h.setStatus("CONCLUDED");
                                h.setConfirmations(confirmations);
                                historyRepository.save(h);
                            });
                        }
                    } catch (Exception ne) {
                        log.error("Erro ao notificar confirmação de depósito: " + ne.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to update receiver balance for tx {}: {}", tx.getTxid(), e.getMessage());
                throw e; // throw so lock is deleted
            }
        } catch (Exception e) {
            // Delete lock so we can retry later
            redisTemplate.delete(idempotencyKey);
            throw e;
        }
    }

    /**
     * Issue 1.5: Constant-time string comparison using MessageDigest.isEqual.
     * Prevents timing attacks where an attacker measures response time to infer
     * whether the status string partially matches the expected value.
     */
    private boolean timingSafeStatusEquals(String current, String expected) {
        if (current == null || expected == null)
            return current == expected;
        byte[] a = current.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /**
     * Registra uma nova transação para monitoramento
     */
    public PendingTransaction registerTransaction(String txid, String fromAddress,
            String toAddress, Long userId,
            BigDecimal amount, Long fee) {
        PendingTransaction entity = new PendingTransaction(txid, fromAddress, toAddress, amount, fee, userId);
        return repository.save(entity);
    }

    /**
     * Busca transação por txid
     */
    public PendingTransaction getTransaction(String txid) {
        return repository.findByTxid(txid);
    }

    /**
     * Lista transações do usuário
     */
    public List<PendingTransaction> getUserTransactions(Long userId) {
        return repository.findByUserId(userId);
    }
}
