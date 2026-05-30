package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends PravoosException {

    public InvalidCredentialsException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED);
    }
}
