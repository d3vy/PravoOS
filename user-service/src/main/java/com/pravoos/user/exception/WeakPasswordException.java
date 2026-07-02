package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class WeakPasswordException extends PravoosException {

    public WeakPasswordException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
    }
}
