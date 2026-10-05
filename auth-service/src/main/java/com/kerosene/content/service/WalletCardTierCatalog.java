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

    /** Immutable tier definitions ordered by the configured display priority. */
    private final List<Tier> tiers;

    /**
     * Creates catalog entries from application configuration, retaining product defaults
     * when a property is absent.
     * @param bronzeFeeRate BRONZE fee as a decimal fraction
     * @param whiteFeeRate WHITE fee as a decimal fraction
     * @param blackFeeRate BLACK fee as a decimal fraction
     * @param bronzeMinMonths minimum BRONZE account age in months
     * @param whiteMinMonths minimum WHITE account age in months
     * @param blackMinMonths minimum BLACK account age in months
     * @param bronzeMinVolume BRONZE monthly volume threshold
     * @param whiteMinVolume WHITE monthly volume threshold
     * @param blackMinVolume BLACK monthly volume threshold
     * @param bronzeAsset media reference for BRONZE
     * @param whiteAsset media reference for WHITE
     * @param blackAsset media reference for BLACK
     */
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

    /** Returns the immutable ordered list of configured wallet-card tiers. */
    public List<Tier> tiers() {
        return tiers;
    }

    /**
     * One card tier's pricing, qualification thresholds, artwork, and feed order.
     * @param code stable tier code used by API and presentation rules
     * @param feeRate fee rate represented as a decimal fraction
     * @param minAccountMonths minimum account tenure required, in months
     * @param minMonthlyVolume minimum monthly volume required
     * @param mediaAsset configured asset reference used by the client card
     * @param priority relative ordering used to present tiers
     */
    public record Tier(
            String code,
            double feeRate,
            int minAccountMonths,
            double minMonthlyVolume,
            String mediaAsset,
            int priority) {

        /** Formats the configured decimal fee as a concise percentage for display. */
        public String formatFeePercent() {
            double percent = feeRate * 100.0d;
            String fixed = String.format(Locale.US, "%.2f", percent);
            fixed = fixed.replaceAll("\\.?0+$", "");
            return fixed + "%";
        }

        /** Formats the monthly volume threshold with locale-independent grouping. */
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
