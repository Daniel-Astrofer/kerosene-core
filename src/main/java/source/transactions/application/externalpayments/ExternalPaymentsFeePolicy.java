package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.transactions.infra.MempoolClient;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ExternalPaymentsFeePolicy {

    private final MempoolClient mempoolClient;
    private final WalletCardProfileService walletCardProfileService;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final long defaultLightningMaxFeeSats;
    private final BigDecimal maxOnchainNetworkFeeBtc;
    private final BigDecimal maxOnchainNetworkFeePercent;

    public ExternalPaymentsFeePolicy(
            MempoolClient mempoolClient,
            WalletCardProfileService walletCardProfileService,
            ExternalPaymentsMath externalPaymentsMath,
            @Value("${lightning.default-max-routing-fee-sats:60}") long defaultLightningMaxFeeSats,
            @Value("${transactions.onchain.max-network-fee-btc:0.00100000}") BigDecimal maxOnchainNetworkFeeBtc,
            @Value("${transactions.onchain.max-network-fee-percent:0.1000}") BigDecimal maxOnchainNetworkFeePercent) {
        this.mempoolClient = mempoolClient;
        this.walletCardProfileService = walletCardProfileService;
        this.externalPaymentsMath = externalPaymentsMath;
        this.defaultLightningMaxFeeSats = defaultLightningMaxFeeSats;
        this.maxOnchainNetworkFeeBtc = positiveOrZero(maxOnchainNetworkFeeBtc);
        this.maxOnchainNetworkFeePercent = positiveOrZero(maxOnchainNetworkFeePercent);
    }

    public BigDecimal estimateOnchainNetworkFee() {
        MempoolClient.RecommendedFees fees = mempoolClient.getRecommendedFees();
        long feeSats = Math.max(1L, fees.halfHourFee() * 225L);
        return externalPaymentsMath.satsToBtc(feeSats);
    }

    public BigDecimal calculateWithdrawalFee(Long userId, BigDecimal amount) {
        return walletCardProfileService.calculateWithdrawalFee(userId, amount);
    }

    public void validateOnchainNetworkFeeCap(BigDecimal amount, BigDecimal networkFee) {
        BigDecimal normalizedFee = externalPaymentsMath.normalizeBtc(networkFee);
        BigDecimal cap = onchainNetworkFeeCap(amount);
        if (cap.compareTo(BigDecimal.ZERO) > 0 && normalizedFee.compareTo(cap) > 0) {
            throw new IllegalArgumentException(
                    "On-chain network fee exceeds configured cap.");
        }
    }

    public long resolveOnchainNetworkFeeCapSats(BigDecimal amount) {
        BigDecimal cap = onchainNetworkFeeCap(amount);
        return cap.compareTo(BigDecimal.ZERO) > 0
                ? externalPaymentsMath.btcToSats(cap)
                : 0L;
    }

    public BigDecimal resolveLightningReservedFee(BigDecimal requestedMaxRoutingFeeBtc) {
        if (requestedMaxRoutingFeeBtc != null) {
            return externalPaymentsMath.normalizeBtc(requestedMaxRoutingFeeBtc);
        }
        return externalPaymentsMath.satsToBtc(defaultLightningMaxFeeSats);
    }

    private BigDecimal onchainNetworkFeeCap(BigDecimal amount) {
        BigDecimal absoluteCap = externalPaymentsMath.normalizeBtc(maxOnchainNetworkFeeBtc);
        BigDecimal percentCap = BigDecimal.ZERO;
        if (amount != null && maxOnchainNetworkFeePercent.compareTo(BigDecimal.ZERO) > 0) {
            percentCap = externalPaymentsMath.normalizeBtc(amount.multiply(maxOnchainNetworkFeePercent));
        }
        if (absoluteCap.compareTo(BigDecimal.ZERO) <= 0) {
            return percentCap;
        }
        if (percentCap.compareTo(BigDecimal.ZERO) <= 0) {
            return absoluteCap;
        }
        return absoluteCap.min(percentCap);
    }

    private BigDecimal positiveOrZero(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value;
    }
}
