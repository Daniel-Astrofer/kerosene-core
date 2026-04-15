package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.transactions.infra.MempoolClient;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;

@Component
public class ExternalPaymentsFeePolicy {

    private final MempoolClient mempoolClient;
    private final WalletCardProfileService walletCardProfileService;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final long defaultLightningMaxFeeSats;

    public ExternalPaymentsFeePolicy(
            MempoolClient mempoolClient,
            WalletCardProfileService walletCardProfileService,
            ExternalPaymentsMath externalPaymentsMath,
            @Value("${lightning.default-max-routing-fee-sats:60}") long defaultLightningMaxFeeSats) {
        this.mempoolClient = mempoolClient;
        this.walletCardProfileService = walletCardProfileService;
        this.externalPaymentsMath = externalPaymentsMath;
        this.defaultLightningMaxFeeSats = defaultLightningMaxFeeSats;
    }

    public BigDecimal estimateOnchainNetworkFee() {
        MempoolClient.RecommendedFees fees = mempoolClient.getRecommendedFees();
        long feeSats = Math.max(1L, fees.halfHourFee() * 225L);
        return externalPaymentsMath.satsToBtc(feeSats);
    }

    public BigDecimal calculateWithdrawalFee(Long userId, BigDecimal amount) {
        return walletCardProfileService.calculateWithdrawalFee(userId, amount);
    }

    public BigDecimal resolveLightningReservedFee(BigDecimal requestedMaxRoutingFeeBtc) {
        if (requestedMaxRoutingFeeBtc != null) {
            return externalPaymentsMath.normalizeBtc(requestedMaxRoutingFeeBtc);
        }
        return externalPaymentsMath.satsToBtc(defaultLightningMaxFeeSats);
    }
}
