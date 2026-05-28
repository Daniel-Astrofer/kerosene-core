package source.ledger.application.transaction;

import java.util.concurrent.TimeUnit;

public interface TransactionIdempotencyPort {

    boolean reserve(String key, long ttl, TimeUnit unit);
}
