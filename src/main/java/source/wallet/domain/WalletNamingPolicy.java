package source.wallet.domain;

import java.util.Locale;

public final class WalletNamingPolicy {

    private WalletNamingPolicy() {
    }

    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public static String normalizeOptionalXpub(String xpub) {
        if (xpub == null) {
            return null;
        }
        String trimmed = xpub.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static source.wallet.model.WalletMode normalizeWalletMode(String walletMode) {
        return source.wallet.model.WalletMode.fromNullable(walletMode);
    }
}
