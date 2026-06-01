package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends PravoosException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Too many failed login attempts. Try again later.", HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
