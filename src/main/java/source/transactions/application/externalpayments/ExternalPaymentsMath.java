package source.transactions.application.externalpayments;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Component
public class ExternalPaymentsMath {

    private final String bitcoinNetwork;

    public ExternalPaymentsMath(@Value("${bitcoin.network:mainnet}") String bitcoinNetwork) {
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

        String normalized = address.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        try {
            Address.fromString(networkParameters(), normalized);
            return true;
        } catch (AddressFormatException exception) {
            return false;
        }
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
            return "mainnet";
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mainnet", "main", "bitcoin", "btc" -> "mainnet";
            case "regtest", "regressiontest" -> "regtest";
            default -> "testnet";
        };
    }

    private NetworkParameters networkParameters() {
        return switch (bitcoinNetwork) {
            case "mainnet" -> MainNetParams.get();
            case "regtest" -> RegTestParams.get();
            default -> TestNet3Params.get();
        };
    }
}
