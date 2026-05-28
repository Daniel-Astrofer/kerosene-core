package source.mining.exception;

public final class MiningExceptions {

    private MiningExceptions() {
    }

    public static class RigNotFound extends RuntimeException {
        public RigNotFound(String message) {
            super(message);
        }
    }

    public static class MiningAllocationNotFound extends RuntimeException {
        public MiningAllocationNotFound(String message) {
            super(message);
        }
    }

    public static class InvalidMiningAllocation extends RuntimeException {
        public InvalidMiningAllocation(String message) {
            super(message);
        }
    }

    public static class MiningAllocationStateException extends RuntimeException {
        public MiningAllocationStateException(String message) {
            super(message);
        }
    }
}
