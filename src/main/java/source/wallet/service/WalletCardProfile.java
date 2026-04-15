package source.wallet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record WalletCardProfile(
        WalletCardType cardType,
        BigDecimal withdrawalFeeRate,
        BigDecimal depositFeeRate,
        BigDecimal monthlyMovement) {

    public BigDecimal calculateWithdrawalFee(BigDecimal amount) {
        return calculateFee(amount, withdrawalFeeRate);
    }

    public BigDecimal calculateDepositFee(BigDecimal amount) {
        return calculateFee(amount, depositFeeRate);
    }

    private BigDecimal calculateFee(BigDecimal amount, BigDecimal rate) {
        if (amount == null || rate == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return amount.multiply(rate).setScale(8, RoundingMode.HALF_UP);
    }
}
