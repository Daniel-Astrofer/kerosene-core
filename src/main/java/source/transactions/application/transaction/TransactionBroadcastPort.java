package source.transactions.application.transaction;

public interface TransactionBroadcastPort {

    String sendRawTransaction(String rawTxHex);
}
