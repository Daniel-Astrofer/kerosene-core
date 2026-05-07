package source.transactions.application.externalpayments;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.model.BlockchainAddressWatchEntity;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.BlockchainAddressWatchService;
import source.transactions.service.NetworkTransferLifecycleService;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Service
public class CancelInboundTransferUseCase {

    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferFactory externalTransferFactory;
    private final LightningInvoiceGateway lightningInvoiceGateway;
    private final BlockchainClient blockchainClient;
    private final BlockchainAddressWatchService blockchainAddressWatchService;
    private final NetworkTransferLifecycleService networkTransferLifecycleService;
    private final WalletRepository walletRepository;

    public CancelInboundTransferUseCase(
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferFactory externalTransferFactory,
            @Qualifier("externalLightningInvoiceGateway")
            LightningInvoiceGateway lightningInvoiceGateway,
            BlockchainClient blockchainClient,
            BlockchainAddressWatchService blockchainAddressWatchService,
            NetworkTransferLifecycleService networkTransferLifecycleService,
            WalletRepository walletRepository) {
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferFactory = externalTransferFactory;
        this.lightningInvoiceGateway = lightningInvoiceGateway;
        this.blockchainClient = blockchainClient;
        this.blockchainAddressWatchService = blockchainAddressWatchService;
        this.networkTransferLifecycleService = networkTransferLifecycleService;
        this.walletRepository = walletRepository;
    }

    @Transactional
    public ExternalTransferResponseDTO cancel(Long userId, UUID transferId) {
        ExternalTransferEntity transfer = externalTransfersPort.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new ExternalPaymentsExceptions.TransferNotFound(
                        "The requested inbound transfer could not be found."));

        if (!isInboundTransfer(transfer)) {
            throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                    "Only inbound deposit flows can be cancelled.");
        }

        if ("CANCELLED".equalsIgnoreCase(transfer.getStatus())) {
            return externalTransferFactory.toResponseDTO(transfer);
        }

        if (!"PENDING".equalsIgnoreCase(transfer.getStatus())) {
            throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                    "Only pending inbound transfers can be cancelled.");
        }

        BlockchainAddressWatchEntity watch = transfer.getId() != null
                ? blockchainAddressWatchService.findByTransferId(transfer.getId()).orElse(null)
                : null;

        if ("INBOUND_INVOICE".equalsIgnoreCase(transfer.getTransferType())) {
            assertLightningInvoiceStillPending(transfer);
            boolean cancelled = lightningInvoiceGateway.cancelLightningInvoice(
                    new CustodyGateway.LightningInvoiceCancellationCommand(
                            transfer.getUserId(),
                            transfer.getWalletId(),
                            transfer.getWalletNameSnapshot(),
                            transfer.getPaymentHash(),
                            transfer.getInvoiceId() != null ? transfer.getInvoiceId() : transfer.getExternalReference(),
                            transfer.getInvoiceData()));
            if (!cancelled) {
                throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                        "The Lightning invoice could not be cancelled by the provider.");
            }
        } else {
            assertOnchainDepositStillCancelable(transfer, watch);
        }

        transfer.setStatus("CANCELLED");
        transfer.setContext(appendContext(transfer.getContext(), "Inbound deposit cancelled by user."));
        ExternalTransferEntity saved = externalTransfersPort.save(transfer);

        if (watch != null) {
            blockchainAddressWatchService.markCancelled(watch);
        }
        clearWalletReceivingAddressIfCurrent(saved, watch);

        return externalTransferFactory.toResponseDTO(saved);
    }

    private boolean isInboundTransfer(ExternalTransferEntity transfer) {
        String type = transfer.getTransferType() != null
                ? transfer.getTransferType().toUpperCase(Locale.ROOT)
                : "";
        return "ADDRESS_ISSUE".equals(type)
                || "ONRAMP_PURCHASE".equals(type)
                || "INBOUND_INVOICE".equals(type);
    }

    private String appendContext(String currentContext, String suffix) {
        if (currentContext == null || currentContext.isBlank()) {
            return suffix;
        }
        if (currentContext.contains(suffix)) {
            return currentContext;
        }
        return currentContext + " | " + suffix;
    }

    private void assertOnchainDepositStillCancelable(
            ExternalTransferEntity transfer,
            BlockchainAddressWatchEntity watch) {
        String watchedAddress = firstNonBlank(
                watch != null ? watch.getAddress() : null,
                transfer.getDestination());

        if (watchedAddress == null || watchedAddress.isBlank()) {
            return;
        }

        OnchainObservation observation = detectOnchainObservation(
                watchedAddress,
                firstNonBlank(
                        transfer.getBlockchainTxid(),
                        watch != null ? watch.getObservedTxid() : null));
        if (observation == null) {
            return;
        }

        if (watch != null) {
            blockchainAddressWatchService.markDetected(
                    watch,
                    observation.txid(),
                    observation.amountSats(),
                    observation.confirmations());
            if (observation.confirmations() > 0) {
                blockchainAddressWatchService.markCompleted(watch, observation.confirmations());
            }
        }

        if (observation.amountSats() > 0L) {
            networkTransferLifecycleService.reconcileOnchainSettlement(
                    transfer,
                    observation.amountSats(),
                    observation.txid(),
                    observation.confirmations(),
                    "CANCEL_VALIDATION");
        }

        throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                "The deposit was already detected on-chain or in the mempool and can no longer be cancelled.");
    }

    private void assertLightningInvoiceStillPending(ExternalTransferEntity transfer) {
        CustodyGateway.IncomingLightningInvoiceStatus status = lightningInvoiceGateway.getLightningInvoiceStatus(
                new CustodyGateway.LightningInvoiceStatusCommand(
                        transfer.getUserId(),
                        transfer.getWalletId(),
                        transfer.getWalletNameSnapshot(),
                        transfer.getPaymentHash(),
                        transfer.getInvoiceId() != null ? transfer.getInvoiceId() : transfer.getExternalReference(),
                        transfer.getInvoiceData()));
        String normalizedStatus = normalize(status.status());
        if (isSettledLightningStatus(normalizedStatus)) {
            networkTransferLifecycleService.reconcileLightningInvoice(
                    transfer,
                    normalizedStatus,
                    status.receivedSats() != null ? status.receivedSats() : 0L,
                    transfer.getPaymentHash(),
                    status.rawPayload(),
                    "CANCEL_VALIDATION");
            throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                    "The Lightning invoice was already paid and can no longer be cancelled.");
        }
        if ("EXPIRED".equals(normalizedStatus) || "CANCELLED".equals(normalizedStatus)) {
            transfer.setStatus(normalizedStatus);
            externalTransfersPort.save(transfer);
            throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                    "The Lightning invoice is no longer pending and cannot be cancelled.");
        }
    }

    private OnchainObservation detectOnchainObservation(String address, String knownTxid) {
        if (knownTxid != null && !knownTxid.isBlank()) {
            JsonNode transaction = blockchainClient.getRawTransaction(knownTxid, true);
            if (transaction != null && !transaction.isNull() && !transaction.isMissingNode()) {
                return new OnchainObservation(
                        knownTxid,
                        extractReceivedAmountSats(transaction, address),
                        confirmationsFromTransaction(transaction));
            }
        }

        JsonNode transactions = blockchainClient.getAddressTransactions(address);
        if (transactions == null || !transactions.isArray()) {
            return null;
        }

        OnchainObservation best = null;
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
                    amountSats = Math.max(amountSats, extractReceivedAmountSats(detailedTransaction, address));
                    confirmations = confirmationsFromTransaction(detailedTransaction);
                }
            }

            if (amountSats <= 0L) {
                amountSats = 1L;
            }

            OnchainObservation candidate = new OnchainObservation(txid, amountSats, confirmations);
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

    private boolean isSettledLightningStatus(String status) {
        return "SETTLED".equals(status)
                || "PAID".equals(status)
                || "COMPLETED".equals(status)
                || "CONFIRMED".equals(status);
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

    private void clearWalletReceivingAddressIfCurrent(
            ExternalTransferEntity transfer,
            BlockchainAddressWatchEntity watch) {
        if (transfer == null || !"ONCHAIN".equalsIgnoreCase(transfer.getNetwork())) {
            return;
        }
        String address = firstNonBlank(
                watch != null ? watch.getAddress() : null,
                transfer.getDestination());
        if (address == null || address.isBlank()) {
            return;
        }

        WalletEntity wallet = walletRepository.findById(transfer.getWalletId()).orElse(null);
        if (wallet == null) {
            return;
        }
        if (!address.equals(wallet.getDepositAddress())) {
            return;
        }

        wallet.setDepositAddress(null);
        wallet.setExternalWalletReference(null);
        walletRepository.save(wallet);
    }

    private record OnchainObservation(
            String txid,
            long amountSats,
            int confirmations) {
    }
}
