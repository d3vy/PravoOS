package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class ApplicationAlreadyExistsException extends PravoosException {

    public ApplicationAlreadyExistsException(String email) {
        super("Pending application already exists for email: " + email, HttpStatus.CONFLICT);
    }
}
