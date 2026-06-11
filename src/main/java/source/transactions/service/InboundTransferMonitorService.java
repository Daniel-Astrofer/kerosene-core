package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.model.BlockchainAddressWatchEntity;
import source.transactions.model.ExternalTransferEntity;
import source.security.VaultKeyProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class InboundTransferMonitorService {

    private static final Logger log = LoggerFactory.getLogger(InboundTransferMonitorService.class);

    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final BlockchainClient blockchainClient;
    private final LightningInvoiceGateway lightningInvoiceGateway;
    private final VaultKeyProvider vaultKeyProvider;
    private final BlockchainAddressWatchService blockchainAddressWatchService;
    private final NetworkTransferLifecycleService networkTransferLifecycleService;
    private final int batchSize;
    private final AtomicBoolean lightningProviderUnavailableLogged = new AtomicBoolean(false);

    public InboundTransferMonitorService(
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsMath externalPaymentsMath,
            BlockchainClient blockchainClient,
            @Qualifier("externalLightningInvoiceGateway")
            LightningInvoiceGateway lightningInvoiceGateway,
            VaultKeyProvider vaultKeyProvider,
            BlockchainAddressWatchService blockchainAddressWatchService,
            NetworkTransferLifecycleService networkTransferLifecycleService,
            @Value("${transactions.inbound-monitor.batch-size:200}") int batchSize) {
        this.externalTransfersPort = externalTransfersPort;
        this.externalPaymentsMath = externalPaymentsMath;
        this.blockchainClient = blockchainClient;
        this.lightningInvoiceGateway = lightningInvoiceGateway;
        this.vaultKeyProvider = vaultKeyProvider;
        this.blockchainAddressWatchService = blockchainAddressWatchService;
        this.networkTransferLifecycleService = networkTransferLifecycleService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${transactions.inbound-monitor.fixed-delay-ms:30000}")
    public void monitorInboundTransfers() {
        if (!vaultKeyProvider.isReady()) {
            log.warn("[InboundMonitor] Skipping cycle: Master key not available yet (STALL mode).");
            return;
        }

        List<ExternalTransferEntity> pending = externalTransfersPort.findInboundTransfersForMonitoring(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        for (ExternalTransferEntity transfer : pending) {
            try {
                monitorSingleTransfer(transfer);
            } catch (Exception ex) {
                log.warn("[InboundMonitor] Failed to inspect transfer {}: {}", transfer.getId(), ex.getMessage(), ex);
            }
        }
    }

    @Transactional
    public void monitorSingleTransfer(ExternalTransferEntity transfer) {
        String type = normalize(transfer.getTransferType());
        if ("INBOUND_INVOICE".equals(type)) {
            inspectLightningInvoice(transfer);
            return;
        }

        if ("ADDRESS_ISSUE".equals(type) || "ONRAMP_PURCHASE".equals(type)) {
            inspectOnchainTransfer(transfer);
        }
    }

    private void inspectOnchainTransfer(ExternalTransferEntity transfer) {
        BlockchainAddressWatchEntity watch = transfer.getId() != null
                ? blockchainAddressWatchService.findByTransferId(transfer.getId()).orElse(null)
                : null;
        String watchedAddress = firstNonBlank(
                watch != null ? watch.getAddress() : null,
                transfer.getDestination());
        String txid = firstNonBlank(
                transfer.getBlockchainTxid(),
                watch != null ? watch.getObservedTxid() : null);
        OnchainAddressObservation observation = null;

        if ((txid == null || txid.isBlank()) && watchedAddress != null && !watchedAddress.isBlank()) {
            observation = detectAddressObservation(watchedAddress);
            if (observation == null) {
                return;
            }
            txid = observation.txid();
        }

        JsonNode transaction = blockchainClient.getRawTransaction(txid, true);
        int confirmations = observation != null
                ? observation.confirmations()
                : confirmationsFromTransaction(transaction);

        long amountSats = watch != null && watch.getObservedAmountSats() != null
                ? watch.getObservedAmountSats()
                : 0L;
        if (amountSats <= 0L && observation != null) {
            amountSats = observation.amountSats();
        }
        if (amountSats <= 0L && watchedAddress != null && !watchedAddress.isBlank()) {
            amountSats = extractReceivedAmountSats(transaction, watchedAddress);
        }
        if (amountSats <= 0L && transfer.getAmountBtc() != null && transfer.getAmountBtc().signum() > 0) {
            amountSats = externalPaymentsMath.btcToSats(transfer.getAmountBtc());
        }
        if (amountSats <= 0L) {
            return;
        }

        ExternalTransferEntity updated = networkTransferLifecycleService.reconcileOnchainSettlement(
                transfer,
                amountSats,
                txid,
                confirmations,
                "INBOUND_MONITOR");
        if (watch != null) {
            blockchainAddressWatchService.markDetected(watch, txid, amountSats, confirmations);
            if ("COMPLETED".equalsIgnoreCase(updated.getStatus())) {
                blockchainAddressWatchService.markCompleted(watch, confirmations);
            }
        }
    }

    private OnchainAddressObservation detectAddressObservation(String address) {
        JsonNode transactions = blockchainClient.getAddressTransactions(address);
        if (transactions == null || !transactions.isArray()) {
            return null;
        }

        OnchainAddressObservation best = null;
        for (JsonNode transaction : transactions) {
            String txid = asText(transaction, "txid");
            if (txid == null || txid.isBlank()) {
                continue;
            }

            JsonNode detailedTransaction = transaction;
            long amountSats = extractReceivedAmountSats(detailedTransaction, address);
            int confirmations = confirmationsFromTransaction(detailedTransaction);
            if (amountSats <= 0L || !detailedTransaction.path("confirmations").isNumber()) {
                JsonNode loaded = blockchainClient.getRawTransaction(txid, true);
                if (loaded != null && !loaded.isNull() && !loaded.isMissingNode()) {
                    detailedTransaction = loaded;
                    amountSats = amountSats > 0L ? amountSats : extractReceivedAmountSats(detailedTransaction, address);
                    confirmations = confirmationsFromTransaction(detailedTransaction);
                }
            }

            if (amountSats <= 0L) {
                continue;
            }

            OnchainAddressObservation candidate = new OnchainAddressObservation(txid, amountSats, confirmations);
            if (best == null || candidate.confirmations() > best.confirmations()) {
                best = candidate;
            }
        }

        return best;
    }

    private long extractReceivedAmountSats(JsonNode transaction, String address) {
        if (transaction == null || transaction.isNull() || transaction.isMissingNode()) {
            return 0L;
        }
        JsonNode outputs = transaction.path("vout");
        if (!outputs.isArray()) {
            return 0L;
        }

        long total = 0L;
        for (JsonNode output : outputs) {
            if (addressMatches(output, address)) {
                total += outputValueSats(output);
            }
        }
        return total;
    }

    private boolean addressMatches(JsonNode output, String address) {
        if (output == null || address == null || address.isBlank()) {
            return false;
        }

        String direct = asText(output, "scriptpubkey_address");
        if (address.equals(direct)) {
            return true;
        }

        JsonNode scriptPubKey = output.path("scriptPubKey");
        if (!scriptPubKey.isMissingNode() && !scriptPubKey.isNull()) {
            if (address.equals(asText(scriptPubKey, "address"))) {
                return true;
            }
            JsonNode addresses = scriptPubKey.path("addresses");
            if (addresses.isArray()) {
                for (JsonNode candidate : addresses) {
                    if (address.equals(candidate.asText())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private long outputValueSats(JsonNode output) {
        JsonNode value = output.path("value");
        if (value.isIntegralNumber()) {
            return Math.max(0L, value.asLong());
        }
        if (value.isNumber()) {
            return btcToSats(value.decimalValue());
        }
        JsonNode satoshis = output.path("satoshis");
        if (satoshis.isIntegralNumber()) {
            return Math.max(0L, satoshis.asLong());
        }
        return 0L;
    }

    private int confirmationsFromTransaction(JsonNode transaction) {
        if (transaction == null || transaction.isNull() || transaction.isMissingNode()) {
            return 0;
        }
        JsonNode confirmations = transaction.path("confirmations");
        if (confirmations.isNumber()) {
            return Math.max(0, confirmations.asInt());
        }
        JsonNode status = transaction.path("status");
        if (status.path("confirmed").asBoolean(false)) {
            return 1;
        }
        return 0;
    }

    private long btcToSats(BigDecimal btc) {
        if (btc == null || btc.signum() <= 0) {
            return 0L;
        }
        return btc.multiply(new BigDecimal("100000000"))
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    private String asText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText();
        return text != null && !text.isBlank() ? text : null;
    }

    private void inspectLightningInvoice(ExternalTransferEntity transfer) {
        if (!lightningInvoiceGateway.isLive()) {
            if (isExpiredPendingLightningInvoice(transfer)) {
                networkTransferLifecycleService.expireLightningInvoice(transfer, "INBOUND_MONITOR");
                return;
            }
            if (lightningProviderUnavailableLogged.compareAndSet(false, true)) {
                log.warn("[InboundMonitor] Skipping Lightning invoice polling because no live custody provider is configured. "
                        + "Pending Lightning transfers will only advance via a real provider callback/webhook after BTCPay/LND is enabled.");
            }
            return;
        }

        CustodyGateway.IncomingLightningInvoiceStatus status = lightningInvoiceGateway.getLightningInvoiceStatus(
                new CustodyGateway.LightningInvoiceStatusCommand(
                        transfer.getUserId(),
                        transfer.getWalletId(),
                        transfer.getWalletNameSnapshot(),
                        transfer.getPaymentHash(),
                        transfer.getInvoiceId() != null ? transfer.getInvoiceId() : transfer.getExternalReference(),
                        transfer.getInvoiceData()));

        String normalizedStatus = normalize(status.status());
        if (isExpiredPendingLightningInvoice(transfer) && !isSettledStatus(normalizedStatus) && !isTerminalStatus(normalizedStatus)) {
            networkTransferLifecycleService.expireLightningInvoice(transfer, "INBOUND_MONITOR");
            return;
        }
        long receivedSats = status.receivedSats() != null
                ? status.receivedSats()
                : 0L;
        if (receivedSats <= 0L
                && isSettledStatus(normalizedStatus)
                && transfer.getAmountBtc() != null
                && transfer.getAmountBtc().signum() > 0) {
            receivedSats = externalPaymentsMath.btcToSats(transfer.getAmountBtc());
        }
        networkTransferLifecycleService.reconcileLightningInvoice(
                transfer,
                normalizedStatus,
                receivedSats,
                transfer.getPaymentHash(),
                status.rawPayload(),
                "INBOUND_MONITOR");
    }

    private boolean isSettledStatus(String status) {
        return "SETTLED".equals(status)
                || "PAID".equals(status)
                || "COMPLETED".equals(status)
                || "CONFIRMED".equals(status);
    }

    private boolean isTerminalStatus(String status) {
        return "EXPIRED".equals(status)
                || "INVALID".equals(status)
                || "CANCELLED".equals(status)
                || "FAILED".equals(status);
    }

    private boolean isExpiredPendingLightningInvoice(ExternalTransferEntity transfer) {
        String currentStatus = normalize(transfer != null ? transfer.getStatus() : null);
        return transfer != null
                && transfer.getExpiresAt() != null
                && transfer.getExpiresAt().isBefore(LocalDateTime.now())
                && !isSettledStatus(currentStatus)
                && !"COMPLETED".equals(currentStatus)
                && !"EXPIRED".equals(currentStatus)
                && !"CANCELLED".equals(currentStatus)
                && !"AUTO_RESOLUTION_PENDING".equals(currentStatus);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record OnchainAddressObservation(
            String txid,
            long amountSats,
            int confirmations) {
    }
}
