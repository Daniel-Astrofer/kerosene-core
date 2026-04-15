package source.transactions.application.externalpayments;

public interface ExternalPaymentsNotificationPort {

    void notifyUser(Long userId, String title, String body);
}
