package source.sovereign.quorum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.security.SuicideService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DefaultFailStopPolicy implements FailStopPolicy {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFailStopPolicy.class);

    private final SuicideService suicideService;
    private final Duration failStopWindow;
    private final AtomicInteger consecutiveQuorumFailures = new AtomicInteger(0);
    private volatile boolean failStopMode = false;
    private volatile Instant lastQuorumSuccess = Instant.now();

    public DefaultFailStopPolicy(
            SuicideService suicideService,
            @Value("${quorum.fail-stop-window-ms:30000}") long failStopWindowMs) {
        this.suicideService = suicideService;
        this.failStopWindow = Duration.ofMillis(failStopWindowMs);
    }

    @Override
    public boolean isFailStopMode() {
        return failStopMode;
    }

    @Override
    public Instant lastQuorumSuccess() {
        return lastQuorumSuccess;
    }

    @Override
    public int consecutiveQuorumFailures() {
        return consecutiveQuorumFailures.get();
    }

    @Override
    public void assertWritesAllowed(String transactionHash) {
        if (!failStopMode) {
            return;
        }

        logger.error("[SPLIT-BRAIN FAIL-STOP] Transaction {} rejected. Writes are suspended.",
                transactionHash);
        throw new SplitBrainException(
                "Kerosene is in Fail-Stop mode due to network partition. "
                        + "Transaction rejected to preserve ledger consistency.");
    }

    @Override
    public void recordQuorumSuccess() {
        consecutiveQuorumFailures.set(0);
        lastQuorumSuccess = Instant.now();
    }

    @Override
    public void recordQuorumFailure(String reason) {
        int failures = consecutiveQuorumFailures.incrementAndGet();
        long msSinceLastSuccess = Duration.between(lastQuorumSuccess, Instant.now()).toMillis();
        logger.warn("[Quorum Fail-Stop] Quorum failure {}. No successful quorum for {} ms. Reason: {}",
                failures, msSinceLastSuccess, reason);

        if (msSinceLastSuccess > failStopWindow.toMillis()) {
            enterFailStop(reason + ". No quorum for " + msSinceLastSuccess + " ms.");
        }
    }

    @Override
    public void enterFailStop(String reason) {
        if (failStopMode) {
            logger.error("[Quorum Fail-Stop] Fail-stop already active. Reason: {}", reason);
            return;
        }

        failStopMode = true;
        logger.error("[CRITICAL] Split-brain risk detected. Entering fail-stop mode. Reason: {}", reason);
        suicideService.triggerInstantSuicide(reason);
    }

    @Override
    public void clearFailStop() {
        failStopMode = false;
        consecutiveQuorumFailures.set(0);
        lastQuorumSuccess = Instant.now();
    }
}
