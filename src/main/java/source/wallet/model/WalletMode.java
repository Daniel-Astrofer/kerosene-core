package source.wallet.model;

import java.util.Locale;

public enum WalletMode {
    KEROSENE,
    SELF_CUSTODY;

    public static WalletMode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return KEROSENE;
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (normalized) {
            case "KEROSENE", "CUSTODIAL" -> KEROSENE;
            case "SELF_CUSTODY", "SELFCUSTODY", "XPUB" -> SELF_CUSTODY;
            default -> throw new IllegalArgumentException("Unsupported wallet mode: " + value);
        };
    }

    public boolean isSelfCustody() {
        return this == SELF_CUSTODY;
    }

    public boolean isKerosene() {
        return this == KEROSENE;
    }
}
