package source.transactions.application.transaction.monitoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PendingTransactionStatusMatcher {

    private PendingTransactionStatusMatcher() {
    }

    public static boolean matches(String current, String expected) {
        if (current == null || expected == null) {
            return current == null && expected == null;
        }

        return MessageDigest.isEqual(
                current.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
