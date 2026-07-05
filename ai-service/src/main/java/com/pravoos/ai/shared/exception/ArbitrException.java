package com.pravoos.ai.shared.exception;

public class ArbitrException extends RuntimeException {

    public ArbitrException(String message) {
        super(message);
    }

    public ArbitrException(String message, Throwable cause) {
        super(message, cause);
    }
}
