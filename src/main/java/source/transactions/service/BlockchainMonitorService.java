package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.transactions.infra.BlockchainInfoClient;
import source.transactions.model.PendingTransaction;
import source.transactions.repository.PendingTransactionRedisRepository;
import source.wallet.service.WalletService;
import source.ledger.service.LedgerService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BlockchainMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainMonitorService.class);
    private static final int MIN_CONFIRMATIONS = 1; // Mínimo de confirmações para considerar confirmado

    private final PendingTransactionRedisRepository repository;
    private final BlockchainInfoClient blockchainClient;
    private final WalletService walletService;
    private final LedgerService ledgerService;

    public BlockchainMonitorService(PendingTransactionRedisRepository repository,
            BlockchainInfoClient blockchainClient,
            WalletService walletService,
            LedgerService ledgerService) {
        this.repository = repository;
        this.blockchainClient = blockchainClient;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
    }

    /**
     * Verifica transações pendentes a cada 30 segundos
     */
    @Scheduled(fixedDelay = 30000) // 30 segundos
    public void monitorPendingTransactions() {
        log.info("Checking pending transactions...");

        List<PendingTransaction> pendingTxs = repository.findByStatus("PENDING");

        if (pendingTxs.isEmpty()) {
            log.info("No pending transactions to monitor");
            return;
        }

        log.info("Found {} pending transaction(s)", pendingTxs.size());

        for (PendingTransaction tx : pendingTxs) {
            try {
                checkTransaction(tx);
            } catch (Exception e) {
                log.error("Error checking transaction {}: {}", tx.getTxid(), e.getMessage());
            }
        }
    }

    /**
     * Verifica status de uma transação específica na blockchain
     */
    public void checkTransaction(PendingTransaction tx) {
        try {
            log.info("Checking transaction: {}", tx.getTxid());

            Map<String, Object> txInfo = blockchainClient.getTransaction(tx.getTxid());

            if (txInfo == null || txInfo.isEmpty()) {
                log.warn("Transaction {} not found on blockchain yet", tx.getTxid());
                return;
            }

            // Extrai confirmações
            Integer confirmations = (Integer) txInfo.getOrDefault("confirmations", 0);
            tx.setConfirmations(confirmations);

            log.info("Transaction {} has {} confirmation(s)", tx.getTxid(), confirmations);

            // Se tem pelo menos 1 confirmação, marca como confirmado
            if (confirmations >= MIN_CONFIRMATIONS) {
                // Check if already confirmed to avoid double deduction
                if (!"CONFIRMED".equals(tx.getStatus())) {
                    tx.setStatus("CONFIRMED");
                    tx.setConfirmedAt(LocalDateTime.now());
                    log.info("Transaction {} CONFIRMED", tx.getTxid());

                    // Deduct balance from wallet
                    try {
                        WalletEntity wallet = walletService.findByAddress(tx.getFromAddress());
                        if (wallet != null) {
                            BigDecimal totalDeduction = tx.getAmount().add(
                                    BigDecimal.valueOf(tx.getFeeSatoshis()).divide(BigDecimal.valueOf(100_000_000)));
                            ledgerService.updateBalance(wallet.getId(), totalDeduction.negate(),
                                    "transfer_out: " + tx.getTxid());
                            log.info("Deducted {} BTC from wallet {} for tx {}", totalDeduction, wallet.getId(),
                                    tx.getTxid());
                        } else {
                            log.error("Wallet not found for address: {}", tx.getFromAddress());
                        }
                    } catch (Exception e) {
                        log.error("Failed to update balance for confirmed tx {}: {}", tx.getTxid(), e.getMessage());
                    }
                }
            }

            repository.save(tx);

        } catch (Exception e) {
            log.error("Error checking transaction {}: {}", tx.getTxid(), e.getMessage());

            // Se erro persistir por muito tempo, marcar como falha
            if (tx.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                tx.setStatus("FAILED");
                tx.setErrorMessage("Transaction not found after 24 hours");
                repository.save(tx);
            }
        }
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
