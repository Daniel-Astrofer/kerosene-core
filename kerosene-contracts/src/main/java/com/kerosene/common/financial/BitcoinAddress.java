package com.kerosene.common.financial;

import java.util.regex.Pattern;

/**
 * Bitcoin address with basic format validation.
 */
public record BitcoinAddress(String value) {
    private static final Pattern BECH32 = Pattern.compile("^(bc|tb|bcrt)1[ac-hj-np-z02-9]{6,}$");
    private static final Pattern BECH32M = Pattern.compile("^(bc|tb|bcrt)1p[ac-hj-np-z02-9]{6,}$");
    private static final Pattern BASE58 = Pattern.compile("^[13mn][1-9A-HJ-NP-Za-km-z]{25,34}$");

    public BitcoinAddress {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("address required");
        if (!BECH32.matcher(value).matches()
                && !BECH32M.matcher(value).matches()
                && !BASE58.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid bitcoin address format: " + value.substring(0, Math.min(value.length(), 10)) + "...");
        }
    }

    @Override
    public String toString() { return value; }
}
