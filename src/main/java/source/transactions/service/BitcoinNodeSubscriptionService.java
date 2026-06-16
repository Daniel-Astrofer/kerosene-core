package source.transactions.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnBean(LndLightningNodeClient.class)
public class BitcoinNodeSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(BitcoinNodeSubscriptionService.class);
    private static final long MIN_RECONNECT_DELAY_SECONDS = 3;
    private static final long MAX_RECONNECT_DELAY_SECONDS = 30;
    private static final long WALLET_LOCKED_RECONNECT_DELAY_SECONDS = 30;

    private final LndLightningNodeClient bitcoinNodeService;
    private final BlockchainAddressWatchService blockchainAddressWatchService;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferRepository externalTransferRepository;
    private final NetworkTransferLifecycleService networkTransferLifecycleService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;
    private final AtomicLong lastInvoiceAddIndex = new AtomicLong();
    private final AtomicLong lastInvoiceSettleIndex = new AtomicLong();
    private volatile long lastLockedWalletWarningMs;

    public BitcoinNodeSubscriptionService(
            @Qualifier("lndLightningGateway") LndLightningNodeClient bitcoinNodeService,
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
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("[BitcoinNodeSubscription] Listener executor did not terminate cleanly.");
        }
    }

    private void runTransactionsLoop() {
        int reconnectAttempts = 0;
        while (running) {
            try {
                Iterator<Transaction> stream = bitcoinNodeService.subscribeTransactions();
                while (running && stream.hasNext()) {
                    reconnectAttempts = 0;
                    processTransaction(stream.next());
                }
                if (running) {
                    reconnectAttempts = sleepBeforeReconnect("Transaction", null, reconnectAttempts);
                }
            } catch (Exception ex) {
                reconnectAttempts = sleepBeforeReconnect("Transaction", ex, reconnectAttempts);
            }
        }
    }

    private void processTransaction(Transaction transaction) {
        Map<String, Long> amountsByAddress = new HashMap<>();
        transaction.getOutputDetailsList().forEach(output -> {
            String address = output.getAddress();
            if (!address.isBlank() && output.getIsOurAddress()) {
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
        int reconnectAttempts = 0;
        while (running) {
            try {
                Iterator<Invoice> stream = bitcoinNodeService.subscribeInvoices(
                        lastInvoiceAddIndex.get(),
                        lastInvoiceSettleIndex.get());
                while (running && stream.hasNext()) {
                    reconnectAttempts = 0;
                    processInvoice(stream.next());
                }
                if (running) {
                    reconnectAttempts = sleepBeforeReconnect("Invoice", null, reconnectAttempts);
                }
            } catch (Exception ex) {
                reconnectAttempts = sleepBeforeReconnect("Invoice", ex, reconnectAttempts);
            }
        }
    }

    private void processInvoice(Invoice invoice) {
        lastInvoiceAddIndex.updateAndGet(current -> Math.max(current, invoice.getAddIndex()));
        lastInvoiceSettleIndex.updateAndGet(current -> Math.max(current, invoice.getSettleIndex()));

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

    private int sleepBeforeReconnect(String streamName, Exception ex, int reconnectAttempts) {
        boolean walletLocked = ex != null && isWalletLocked(ex);
        int nextAttempts = walletLocked ? 0 : Math.min(reconnectAttempts + 1, 4);
        long delaySeconds = walletLocked
                ? WALLET_LOCKED_RECONNECT_DELAY_SECONDS
                : Math.min(MAX_RECONNECT_DELAY_SECONDS,
                        MIN_RECONNECT_DELAY_SECONDS << Math.max(0, nextAttempts - 1));

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
        } else if (ex == null) {
            log.warn("[BitcoinNodeSubscription] {} stream ended; reconnecting in {}s.", streamName, delaySeconds);
        } else {
            log.warn("[BitcoinNodeSubscription] {} stream interrupted: {}; reconnecting in {}s.",
                    streamName, ex.getMessage(), delaySeconds);
        }

        try {
            TimeUnit.SECONDS.sleep(delaySeconds);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return nextAttempts;
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
