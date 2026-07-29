package com.kerosene.content.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Platform wallet-card tiers (BRONZE / WHITE / BLACK) with fee rates and
 * upgrade rules. Values come from configuration so the home education feed
 * stays reactive to backend policy without hardcoding in the client.
 */
@Component
public class WalletCardTierCatalog {

    private final List<Tier> tiers;

    public WalletCardTierCatalog(
            @Value("${wallet.card.bronze.fee-rate:0.009}") double bronzeFeeRate,
            @Value("${wallet.card.white.fee-rate:0.008}") double whiteFeeRate,
            @Value("${wallet.card.black.fee-rate:0.007}") double blackFeeRate,
            @Value("${wallet.card.bronze.min-account-months:0}") int bronzeMinMonths,
            @Value("${wallet.card.white.min-account-months:6}") int whiteMinMonths,
            @Value("${wallet.card.black.min-account-months:12}") int blackMinMonths,
            @Value("${wallet.card.bronze.min-monthly-volume:0}") double bronzeMinVolume,
            @Value("${wallet.card.white.min-monthly-volume:1500}") double whiteMinVolume,
            @Value("${wallet.card.black.min-monthly-volume:4000}") double blackMinVolume,
            @Value("${wallet.card.bronze.asset:asset:assets/feed/cards/bronze.png}") String bronzeAsset,
            @Value("${wallet.card.white.asset:asset:assets/feed/cards/metal.png}") String whiteAsset,
            @Value("${wallet.card.black.asset:asset:assets/feed/cards/gold.png}") String blackAsset) {
        this.tiers = List.of(
                new Tier(
                        "BRONZE",
                        bronzeFeeRate,
                        bronzeMinMonths,
                        bronzeMinVolume,
                        bronzeAsset,
                        200),
                new Tier(
                        "WHITE",
                        whiteFeeRate,
                        whiteMinMonths,
                        whiteMinVolume,
                        whiteAsset,
                        199),
                new Tier(
                        "BLACK",
                        blackFeeRate,
                        blackMinMonths,
                        blackMinVolume,
                        blackAsset,
                        198));
    }

    public List<Tier> tiers() {
        return tiers;
    }

    public record Tier(
            String code,
            double feeRate,
            int minAccountMonths,
            double minMonthlyVolume,
            String mediaAsset,
            int priority) {

        public String formatFeePercent() {
            double percent = feeRate * 100.0d;
            String fixed = String.format(Locale.US, "%.2f", percent);
            fixed = fixed.replaceAll("\\.?0+$", "");
            return fixed + "%";
        }

        public String formatVolume() {
            if (minMonthlyVolume <= 0) {
                return "0";
            }
            if (Math.abs(minMonthlyVolume - Math.rint(minMonthlyVolume)) < 0.0001d) {
                return String.format(Locale.US, "%,.0f", minMonthlyVolume);
            }
            return String.format(Locale.US, "%,.2f", minMonthlyVolume);
        }
    }
}
