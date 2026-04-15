package source.transactions.application.externalpayments;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ExternalPaymentsMath {

    public void validatePositiveAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public boolean isValidBitcoinAddress(String address) {
        return address != null && address.matches("^(1|3|bc1)[a-zA-Z0-9]{25,62}$");
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
}
