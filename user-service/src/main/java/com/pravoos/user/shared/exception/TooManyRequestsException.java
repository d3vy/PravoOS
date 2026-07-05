package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends PravoosException {

    public TooManyRequestsException() {
        super("Слишком много запросов. Попробуйте позже.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
