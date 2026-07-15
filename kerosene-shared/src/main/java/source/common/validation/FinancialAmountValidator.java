package source.common.validation;

import java.math.BigDecimal;

public final class FinancialAmountValidator {

    public static final int BTC_SCALE = 8;
    public static final BigDecimal MAX_BTC_AMOUNT = new BigDecimal("21000000.00000000");

    private FinancialAmountValidator() {
    }

    public static void requirePositiveBtc(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
        requireBtcPrecision(amount, fieldName);
    }

    public static void requireNonZeroBtcDelta(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException(fieldName + " must not be zero.");
        }
        requireBtcPrecision(amount.abs(), fieldName);
    }

    public static void requireBtcPrecision(BigDecimal amount, String fieldName) {
        if (amount.scale() > BTC_SCALE) {
            throw new IllegalArgumentException(fieldName + " supports at most 8 decimal places.");
        }
        if (amount.abs().compareTo(MAX_BTC_AMOUNT) > 0) {
            throw new IllegalArgumentException(fieldName + " exceeds the maximum supported BTC amount.");
        }
    }
}
