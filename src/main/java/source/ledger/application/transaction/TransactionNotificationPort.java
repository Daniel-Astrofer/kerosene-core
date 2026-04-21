package source.ledger.application.transaction;

public interface TransactionNotificationPort {

    void notifyUser(Long userId, String title, String message);
}
