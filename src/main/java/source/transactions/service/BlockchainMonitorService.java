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
    private final source.notification.service.NotificationService notificationService;

    public BlockchainMonitorService(PendingTransactionRedisRepository repository,
            BlockchainInfoClient blockchainClient,
            WalletService walletService,
            LedgerService ledgerService,
            source.notification.service.NotificationService notificationService) {
        this.repository = repository;
        this.blockchainClient = blockchainClient;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
    }

    /**
     * Verifica transações pendentes a cada 30 segundos
     */
    @Scheduled(fixedDelay = 30000) // 30 segundos
    public void monitorPendingTransactions() {
        List<PendingTransaction> pendingTxs = repository.findByStatus("PENDING");

        if (pendingTxs.isEmpty()) {
            return;
        }

        log.info("Checking pending transactions...");
        log.info("Found {} pending transaction(s)", pendingTxs.size());

        for (PendingTransaction tx : pendingTxs) {
            try {
                // Fail old mock transactions immediately to clean up queue
                if (tx.getTxid().startsWith("mock-") || tx.getTxid().length() != 64) {
                    log.warn("Marking INVALID transaction as FAILED: {}", tx.getTxid());
                    tx.setStatus("FAILED");
                    tx.setErrorMessage("Invalid TXID format - Cleanup");
                    repository.save(tx);
                    continue;
                }

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

            log.info("Transaction {} has {} confirmation(s)", tx.getTxid(), confirmations);

            // Se tem pelo menos 1 confirmação, marca como confirmado
            if (confirmations >= MIN_CONFIRMATIONS) {
                // Check if already confirmed to avoid double deduction
                if (!"CONFIRMED".equals(tx.getStatus())) {
                    tx.setStatus("CONFIRMED");
                    tx.setConfirmedAt(LocalDateTime.now());
                    log.info("Transaction {} CONFIRMED", tx.getTxid());

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
                            } catch (Exception ne) {
                                log.error("Erro ao notificar confirmação de envio: " + ne.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to update sender balance for tx {}: {}", tx.getTxid(), e.getMessage());
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
                            } catch (Exception ne) {
                                log.error("Erro ao notificar confirmação de depósito: " + ne.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to update receiver balance for tx {}: {}", tx.getTxid(), e.getMessage());
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
