package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends PravoosException {

    public EmailAlreadyExistsException(String email) {
        super("User with this email already exists: " + email, HttpStatus.CONFLICT);
    }
}
