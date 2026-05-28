package source.bitcoinaccounts.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ReceivingAddressEntity;
import source.bitcoinaccounts.repository.ReceivingAddressRepository;
import source.common.infra.logging.LogSanitizer;
import source.transactions.infra.BlockchainClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BitcoinReceivingMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BitcoinReceivingMonitorService.class);
    private static final BigDecimal SATOSHIS_PER_BITCOIN = new BigDecimal("100000000");
    private static final List<BitcoinAccountEnums.ReceivingAddressStatus> MONITORED_STATUSES = List.of(
            BitcoinAccountEnums.ReceivingAddressStatus.ASSIGNED,
            BitcoinAccountEnums.ReceivingAddressStatus.OBSERVED,
            BitcoinAccountEnums.ReceivingAddressStatus.EXPIRED,
            BitcoinAccountEnums.ReceivingAddressStatus.EXPIRED_RECEIVED,
            BitcoinAccountEnums.ReceivingAddressStatus.USER_ACTION_REQUIRED);

    private final ReceivingAddressRepository addressRepository;
    private final ReceivingRequestService receivingRequestService;
    private final BlockchainClient blockchainClient;

    public BitcoinReceivingMonitorService(
            ReceivingAddressRepository addressRepository,
            ReceivingRequestService receivingRequestService,
            BlockchainClient blockchainClient) {
        this.addressRepository = addressRepository;
        this.receivingRequestService = receivingRequestService;
        this.blockchainClient = blockchainClient;
    }

    @Scheduled(fixedDelayString = "${bitcoin-accounts.receive-monitor.fixed-delay-ms:30000}")
    public void scanReceivingAddresses() {
        for (ReceivingAddressEntity address : addressRepository
                .findTop200ByStatusInOrderByUpdatedAtAsc(MONITORED_STATUSES)) {
            try {
                scanAddress(address);
            } catch (Exception ex) {
                log.warn("[BitcoinReceivingMonitor] Scan failed for addressRef={}: {}",
                        LogSanitizer.fingerprint(address.getAddress()),
                        ex.getMessage());
            }
        }
    }

    private void scanAddress(ReceivingAddressEntity address) {
        JsonNode transactions = blockchainClient.getAddressTransactions(address.getAddress());
        boolean observed = false;
        if (transactions != null && transactions.isArray()) {
            for (JsonNode transaction : transactions) {
                observed |= observeTransactionOrReference(address.getAddress(), transaction);
            }
        }

        if (!observed && address.getFirstSeenTxid() != null && !address.getFirstSeenTxid().isBlank()) {
            observeRawTransaction(address.getAddress(), address.getFirstSeenTxid());
        }
    }

    private boolean observeTransactionOrReference(String address, JsonNode transaction) {
        if (transaction == null || transaction.isNull() || transaction.isMissingNode()) {
            return false;
        }

        boolean observed = false;
        String txid = text(transaction, "txid");
        if (txid != null) {
            JsonNode detailed = transaction.path("vout").isArray()
                    ? transaction
                    : blockchainClient.getRawTransaction(txid, true);
            observed |= observeOutputs(address, txid, detailed);
        }

        JsonNode txids = transaction.path("txids");
        if (txids.isArray()) {
            for (JsonNode candidate : txids) {
                String candidateTxid = candidate.asText(null);
                if (candidateTxid != null && !candidateTxid.isBlank()) {
                    observed |= observeRawTransaction(address, candidateTxid);
                }
            }
        }
        return observed;
    }

    private boolean observeRawTransaction(String address, String txid) {
        JsonNode detailed = blockchainClient.getRawTransaction(txid, true);
        return observeOutputs(address, txid, detailed);
    }

    private boolean observeOutputs(String address, String txid, JsonNode transaction) {
        if (txid == null || txid.isBlank()
                || transaction == null
                || transaction.isNull()
                || transaction.isMissingNode()) {
            return false;
        }

        JsonNode outputs = transaction.path("vout");
        if (!outputs.isArray()) {
            return false;
        }

        int confirmations = confirmations(transaction);
        boolean observed = false;
        for (int index = 0; index < outputs.size(); index++) {
            JsonNode output = outputs.get(index);
            if (!matchesAddress(output, address)) {
                continue;
            }
            long amountSats = outputValueSats(output);
            if (amountSats <= 0L) {
                continue;
            }
            int vout = output.path("n").isIntegralNumber() ? output.path("n").asInt() : index;
            receivingRequestService.observeOnchainPayment(address, txid, vout, amountSats, confirmations);
            observed = true;
        }
        return observed;
    }

    private boolean matchesAddress(JsonNode output, String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        if (address.equals(text(output, "scriptpubkey_address"))) {
            return true;
        }
        JsonNode scriptPubKey = output.path("scriptPubKey");
        if (address.equals(text(scriptPubKey, "address"))) {
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

    private int confirmations(JsonNode transaction) {
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
        return btc.multiply(SATOSHIS_PER_BITCOIN)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText();
        return text != null && !text.isBlank() ? text : null;
    }
}
