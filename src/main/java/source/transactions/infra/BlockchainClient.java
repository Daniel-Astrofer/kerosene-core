package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface BlockchainClient {
    record FeeRates(long fastSatPerVByte, long halfHourSatPerVByte, long hourSatPerVByte) {
        public FeeRates {
            fastSatPerVByte = Math.max(1L, fastSatPerVByte);
            halfHourSatPerVByte = Math.max(1L, halfHourSatPerVByte);
            hourSatPerVByte = Math.max(1L, hourSatPerVByte);
        }
    }

    JsonNode executeRpc(String method, Object... params);

    String sendRawTransaction(String hex);

    JsonNode getRawTransaction(String txid, boolean verbose);

    default long getHotWalletBalance() {
        try {
            JsonNode balances = unwrapResult(executeRpc("getbalances"));
            long balance = parseBtcBalanceToSats(balances);
            if (balance > 0L) {
                return balance;
            }

            JsonNode legacyBalance = unwrapResult(executeRpc("getbalance"));
            return parseBtcBalanceToSats(legacyBalance);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    default FeeRates estimateSmartFee(int fastBlocks, int halfHourBlocks, int hourBlocks) {
        return new FeeRates(
                estimateSmartFeeForTarget(fastBlocks, 50L),
                estimateSmartFeeForTarget(halfHourBlocks, 25L),
                estimateSmartFeeForTarget(hourBlocks, 10L));
    }

    default JsonNode getAddressTransactions(String address) {
        if (address == null || address.isBlank()) {
            return JsonNodeFactory.instance.arrayNode();
        }

        try {
            JsonNode txs = unwrapResult(executeRpc("listreceivedbyaddress", 0, true, true, address));
            return txs != null && txs.isArray() ? txs : JsonNodeFactory.instance.arrayNode();
        } catch (RuntimeException e) {
            return JsonNodeFactory.instance.arrayNode();
        }
    }

    private long estimateSmartFeeForTarget(int confirmationTarget, long fallbackSatPerVByte) {
        if (confirmationTarget <= 0) {
            return fallbackSatPerVByte;
        }

        try {
            JsonNode feeEstimate = unwrapResult(executeRpc("estimatesmartfee", confirmationTarget));
            JsonNode feeRateNode = feeEstimate != null ? feeEstimate.path("feerate") : null;
            if (feeRateNode == null || !feeRateNode.isNumber()) {
                return fallbackSatPerVByte;
            }

            BigDecimal btcPerKvB = feeRateNode.decimalValue();
            if (btcPerKvB.signum() <= 0) {
                return fallbackSatPerVByte;
            }

            return Math.max(1L, btcPerKvB
                    .multiply(new BigDecimal("100000000"))
                    .divide(new BigDecimal("1000"), 0, RoundingMode.CEILING)
                    .longValue());
        } catch (RuntimeException e) {
            return fallbackSatPerVByte;
        }
    }

    private static JsonNode unwrapResult(JsonNode node) {
        if (node != null && node.has("result") && !node.get("result").isNull()) {
            return node.get("result");
        }
        return node;
    }

    private static long parseBtcBalanceToSats(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return 0L;
        }

        if (node.isNumber()) {
            return btcToSats(node.decimalValue());
        }

        JsonNode mine = node.path("mine");
        if (!mine.isMissingNode()) {
            BigDecimal total = decimalField(mine, "trusted")
                    .add(decimalField(mine, "untrusted_pending"))
                    .add(decimalField(mine, "immature"));
            return btcToSats(total);
        }

        if (node.path("balance").isNumber()) {
            return btcToSats(node.path("balance").decimalValue());
        }

        return 0L;
    }

    private static BigDecimal decimalField(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }

    private static long btcToSats(BigDecimal btc) {
        if (btc == null || btc.signum() <= 0) {
            return 0L;
        }
        return btc.multiply(new BigDecimal("100000000"))
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }
}
