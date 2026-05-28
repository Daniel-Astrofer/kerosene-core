package source.transactions.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.infra.lnd.proto.lnrpc.Invoice;
import source.transactions.infra.lnd.proto.lnrpc.Transaction;
import source.transactions.model.BlockchainAddressWatchEntity;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalTransferRepository;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnBean(BitcoinNodeService.class)
public class BitcoinNodeSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(BitcoinNodeSubscriptionService.class);

    private final BitcoinNodeService bitcoinNodeService;
    private final BlockchainAddressWatchService blockchainAddressWatchService;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferRepository externalTransferRepository;
    private final NetworkTransferLifecycleService networkTransferLifecycleService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;
    private volatile long lastInvoiceAddIndex;
    private volatile long lastInvoiceSettleIndex;
    private volatile long lastLockedWalletWarningMs;

    public BitcoinNodeSubscriptionService(
            BitcoinNodeService bitcoinNodeService,
            BlockchainAddressWatchService blockchainAddressWatchService,
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferRepository externalTransferRepository,
            NetworkTransferLifecycleService networkTransferLifecycleService) {
        this.bitcoinNodeService = bitcoinNodeService;
        this.blockchainAddressWatchService = blockchainAddressWatchService;
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferRepository = externalTransferRepository;
        this.networkTransferLifecycleService = networkTransferLifecycleService;
    }

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void start() {
        if (!bitcoinNodeService.isLive()) {
            return;
        }
        executor.submit(this::runTransactionsLoop);
        executor.submit(this::runInvoicesLoop);
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        running = false;
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private void runTransactionsLoop() {
        while (running) {
            try {
                Iterator<Transaction> stream = bitcoinNodeService.subscribeTransactions();
                while (running && stream.hasNext()) {
                    processTransaction(stream.next());
                }
            } catch (Exception ex) {
                sleepBeforeReconnect("Transaction", ex);
            }
        }
    }

    private void processTransaction(Transaction transaction) {
        Map<String, Long> amountsByAddress = new HashMap<>();
        transaction.getOutputDetailsList().forEach(output -> {
            String address = output.getAddress();
            if (address != null && !address.isBlank() && output.getIsOurAddress()) {
                amountsByAddress.merge(address, output.getAmount(), Long::sum);
            }
        });

        amountsByAddress.forEach((address, amountSats) -> blockchainAddressWatchService.findActiveWatch(address)
                .ifPresent(watch -> reconcileOnchainWatch(watch, transaction, amountSats)));
    }

    private void reconcileOnchainWatch(
            BlockchainAddressWatchEntity watch,
            Transaction transaction,
            long amountSats) {
        UUID transferId = watch.getTransferId();
        ExternalTransferEntity transfer = transferId != null
                ? externalTransfersPort.findById(transferId).orElse(null)
                : null;
        if (transfer == null) {
            return;
        }

        ExternalTransferEntity updated = networkTransferLifecycleService.reconcileOnchainSettlement(
                transfer,
                amountSats,
                transaction.getTxHash(),
                Math.max(0, transaction.getNumConfirmations()),
                "LND_SUBSCRIBE_TRANSACTIONS");
        blockchainAddressWatchService.markDetected(
                watch,
                transaction.getTxHash(),
                amountSats,
                Math.max(0, transaction.getNumConfirmations()));
        if ("COMPLETED".equalsIgnoreCase(updated.getStatus()) || "CONFIRMED".equalsIgnoreCase(updated.getStatus())) {
            blockchainAddressWatchService.markCompleted(watch, Math.max(0, transaction.getNumConfirmations()));
        }
    }

    private void runInvoicesLoop() {
        while (running) {
            try {
                Iterator<Invoice> stream = bitcoinNodeService.subscribeInvoices(lastInvoiceAddIndex, lastInvoiceSettleIndex);
                while (running && stream.hasNext()) {
                    processInvoice(stream.next());
                }
            } catch (Exception ex) {
                sleepBeforeReconnect("Invoice", ex);
            }
        }
    }

    private void processInvoice(Invoice invoice) {
        lastInvoiceAddIndex = Math.max(lastInvoiceAddIndex, invoice.getAddIndex());
        lastInvoiceSettleIndex = Math.max(lastInvoiceSettleIndex, invoice.getSettleIndex());

        String paymentHash = java.util.HexFormat.of().formatHex(invoice.getRHash().toByteArray());
        ExternalTransferEntity transfer = externalTransferRepository.findTopByPaymentHashOrderByCreatedAtDesc(paymentHash)
                .orElse(null);
        if (transfer == null) {
            return;
        }

        long receivedSats = invoice.getAmtPaidSat() > 0 ? invoice.getAmtPaidSat() : invoice.getValue();
        networkTransferLifecycleService.reconcileLightningInvoice(
                transfer,
                bitcoinNodeService.mapInvoiceStatus(invoice),
                receivedSats,
                paymentHash,
                invoice.toString(),
                "LND_SUBSCRIBE_INVOICES");
    }

    private void sleepBeforeReconnect(String streamName, Exception ex) {
        boolean walletLocked = isWalletLocked(ex);
        if (walletLocked) {
            long now = System.currentTimeMillis();
            if (now - lastLockedWalletWarningMs >= TimeUnit.MINUTES.toMillis(1)) {
                lastLockedWalletWarningMs = now;
                log.warn("[BitcoinNodeSubscription] LND wallet is locked; {} stream paused until wallet unlocks.",
                        streamName);
            } else {
                log.debug("[BitcoinNodeSubscription] LND wallet still locked; {} stream remains paused.",
                        streamName);
            }
        } else {
            log.warn("[BitcoinNodeSubscription] {} stream interrupted: {}", streamName, ex.getMessage());
        }

        try {
            TimeUnit.SECONDS.sleep(walletLocked ? 30 : 3);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isWalletLocked(Exception ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("wallet locked")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
