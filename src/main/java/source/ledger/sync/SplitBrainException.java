package source.ledger.sync;

public class SplitBrainException extends RuntimeException {

    public SplitBrainException(String message) {
        super(message);
    }
}
