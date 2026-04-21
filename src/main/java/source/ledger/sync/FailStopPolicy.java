package source.ledger.sync;

import java.time.Instant;

public interface FailStopPolicy {

    boolean isFailStopMode();

    Instant lastQuorumSuccess();

    int consecutiveQuorumFailures();

    void assertWritesAllowed(String transactionHash);

    void recordQuorumSuccess();

    void recordQuorumFailure(String reason);

    void enterFailStop(String reason);

    void clearFailStop();
}
