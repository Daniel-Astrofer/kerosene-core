package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Component
public class ExternalPaymentsMath {

    private final String bitcoinNetwork;

    public ExternalPaymentsMath(@Value("${bitcoin.network:testnet}") String bitcoinNetwork) {
        this.bitcoinNetwork = normalizeBitcoinNetwork(bitcoinNetwork);
    }

    public void validatePositiveAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public boolean isValidBitcoinAddress(String address) {
        if (address == null) {
            return false;
        }

        String normalized = address.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }

        return switch (bitcoinNetwork) {
            case "mainnet" -> normalized.matches("^(1|3|bc1)[a-z0-9]{25,90}$");
            case "regtest" -> normalized.matches("^(m|n|2|bcrt1)[a-z0-9]{20,90}$");
            default -> normalized.matches("^(m|n|2|tb1)[a-z0-9]{20,90}$");
        };
    }

    public String configuredBitcoinNetwork() {
        return bitcoinNetwork;
    }

    public long btcToSats(BigDecimal btc) {
        return btc.multiply(new BigDecimal("100000000"))
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    public BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
    }

    public BigDecimal normalizeBtc(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    public BigDecimal nullableNormalizeBtc(BigDecimal value) {
        return value == null ? null : normalizeBtc(value);
    }

    public String safeText(String value) {
        return value != null ? value : "";
    }

    public String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeBitcoinNetwork(String value) {
        if (value == null || value.isBlank()) {
            return "testnet";
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mainnet", "bitcoin", "btc" -> "mainnet";
            case "regtest", "regressiontest" -> "regtest";
            default -> "testnet";
        };
    }
}
