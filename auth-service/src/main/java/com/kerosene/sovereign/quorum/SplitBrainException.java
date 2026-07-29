package com.kerosene.sovereign.quorum;

public class SplitBrainException extends RuntimeException {

    public SplitBrainException(String message) {
        super(message);
    }
}
