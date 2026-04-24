package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.infra.BlockchainClient;
import source.transactions.model.BlockchainAddressWatchEntity;
import source.transactions.model.ExternalTransferEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(prefix = "bitcoin.rpc.zmq", name = "enabled", havingValue = "true")
public class BlockchainZmqListenerService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainZmqListenerService.class);
    private static final BigDecimal SATOSHIS_PER_BITCOIN = new BigDecimal("100000000");

    private final BlockchainClient blockchainClient;
    private final ExternalTransfersPort externalTransfersPort;
    private final BlockchainAddressWatchService blockchainAddressWatchService;
    private final NetworkTransferLifecycleService lifecycleService;
    private final NetworkTransferEventService networkTransferEventService;
    private final String rawTxEndpoint;
    private final String hashBlockEndpoint;
    private final NetworkParameters networkParameters;
    private final int minimumConfirmations;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "bitcoin-zmq-listener");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BlockchainZmqListenerService(
            BlockchainClient blockchainClient,
            ExternalTransfersPort externalTransfersPort,
            BlockchainAddressWatchService blockchainAddressWatchService,
            NetworkTransferLifecycleService lifecycleService,
            NetworkTransferEventService networkTransferEventService,
            @Value("${bitcoin.rpc.zmq.rawtx}") String rawTxEndpoint,
            @Value("${bitcoin.rpc.zmq.hashblock}") String hashBlockEndpoint,
            @Value("${bitcoin.network:testnet}") String bitcoinNetwork,
            @Value("${bitcoin.min-confirmations:3}") int minimumConfirmations) {
        this.blockchainClient = blockchainClient;
        this.externalTransfersPort = externalTransfersPort;
        this.blockchainAddressWatchService = blockchainAddressWatchService;
        this.lifecycleService = lifecycleService;
        this.networkTransferEventService = networkTransferEventService;
        this.rawTxEndpoint = rawTxEndpoint != null ? rawTxEndpoint.trim() : "";
        this.hashBlockEndpoint = hashBlockEndpoint != null ? hashBlockEndpoint.trim() : "";
        this.networkParameters = resolveNetworkParameters(bitcoinNetwork);
        this.minimumConfirmations = Math.max(1, minimumConfirmations);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (rawTxEndpoint.isBlank() || hashBlockEndpoint.isBlank()) {
            log.warn("[BlockchainZmq] ZMQ listener configured without endpoints. rawtx='{}' hashblock='{}'",
                    rawTxEndpoint,
                    hashBlockEndpoint);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor.submit(this::listen);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
    }

    private void listen() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.SUB);
            socket.subscribe("rawtx".getBytes(ZMQ.CHARSET));
            socket.subscribe("hashblock".getBytes(ZMQ.CHARSET));
            socket.connect(rawTxEndpoint);
            if (!hashBlockEndpoint.equals(rawTxEndpoint)) {
                socket.connect(hashBlockEndpoint);
            }

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                byte[] topicBytes = socket.recv(ZMQ.DONTWAIT);
                if (topicBytes == null) {
                    Thread.sleep(100L);
                    continue;
                }

                String topic = new String(topicBytes, ZMQ.CHARSET);
                byte[] payload = socket.recv();
                drainMultipart(socket);

                if ("rawtx".equals(topic)) {
                    handleRawTransaction(payload);
                } else if ("hashblock".equals(topic)) {
                    handleNewBlock(payload);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("[BlockchainZmq] Listener terminated unexpectedly: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }

    private void handleRawTransaction(byte[] payload) {
        try {
            Transaction transaction = new Transaction(networkParameters, payload);
            String txid = transaction.getTxId().toString();

            for (TransactionOutput output : transaction.getOutputs()) {
                Address address = extractAddress(output);
                if (address == null) {
                    continue;
                }

                BlockchainAddressWatchEntity watch = blockchainAddressWatchService.findActiveWatch(address.toString())
                        .orElse(null);
                if (watch == null) {
                    continue;
                }

                ExternalTransferEntity transfer = externalTransfersPort.findById(watch.getTransferId()).orElse(null);
                if (transfer == null) {
                    continue;
                }

                long amountSats = output.getValue().getValue();
                blockchainAddressWatchService.markDetected(watch, txid, amountSats, 0);
                lifecycleService.reconcileOnchainSettlement(
                        transfer,
                        amountSats,
                        txid,
                        0,
                        "BITCOIN_ZMQ_RAWTX");
            }
        } catch (Exception ex) {
            log.warn("[BlockchainZmq] Failed to process rawtx event: {}", ex.getMessage());
        }
    }

    private void handleNewBlock(byte[] payload) {
        String blockHash = reverseHex(payload);
        networkTransferEventService.info((Long) null, "BITCOIN_BLOCK_SEEN", blockHash, "hashblock");

        List<ExternalTransferEntity> transfers = externalTransfersPort.findOnchainTransfersForMonitoring(200);
        for (ExternalTransferEntity transfer : transfers) {
            String txid = transfer.getBlockchainTxid();
            if (txid == null || txid.isBlank()) {
                continue;
            }

            try {
                JsonNode transaction = blockchainClient.getRawTransaction(txid, true);
                int confirmations = transaction != null && transaction.path("confirmations").isNumber()
                        ? transaction.path("confirmations").asInt()
                        : 0;
                long amountSats = btcToSats(transfer.getAmountBtc());
                lifecycleService.reconcileOnchainSettlement(
                        transfer,
                        amountSats,
                        txid,
                        confirmations,
                        "BITCOIN_ZMQ_BLOCK");

                blockchainAddressWatchService.findByTransferId(transfer.getId()).ifPresent(watch -> {
                    if (confirmations >= 0) {
                        blockchainAddressWatchService.markDetected(
                                watch,
                                txid,
                                amountSats,
                                confirmations);
                    }
                    if (confirmations >= minimumConfirmations) {
                        blockchainAddressWatchService.markCompleted(watch, confirmations);
                    }
                });
            } catch (Exception ex) {
                log.warn("[BlockchainZmq] Failed to reconcile tx {} after block {}: {}", txid, blockHash, ex.getMessage());
            }
        }
    }

    private void drainMultipart(ZMQ.Socket socket) {
        while (socket.hasReceiveMore()) {
            socket.recv();
        }
    }

    private Address extractAddress(TransactionOutput output) {
        try {
            return output.getScriptPubKey().getToAddress(networkParameters, true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long btcToSats(BigDecimal btc) {
        if (btc == null) {
            return 0L;
        }
        return btc.multiply(SATOSHIS_PER_BITCOIN)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    private String reverseHex(byte[] bytes) {
        byte[] reversed = new byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            reversed[index] = bytes[bytes.length - 1 - index];
        }
        return java.util.HexFormat.of().formatHex(reversed);
    }

    private NetworkParameters resolveNetworkParameters(String bitcoinNetwork) {
        String normalized = bitcoinNetwork != null ? bitcoinNetwork.toLowerCase(Locale.ROOT) : "";
        return "mainnet".equals(normalized) ? MainNetParams.get() : TestNet3Params.get();
    }
}
