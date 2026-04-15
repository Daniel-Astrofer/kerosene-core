package source.transactions.application.transaction;

import java.math.BigDecimal;

public interface TransactionNotificationPort {

    void notifySenderBroadcast(Long userId, BigDecimal amount);

    void notifyRecipientBroadcast(String address, BigDecimal amount, String message);
}
