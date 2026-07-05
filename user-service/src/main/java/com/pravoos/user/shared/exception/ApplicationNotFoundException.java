package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ApplicationNotFoundException extends PravoosException {

    public ApplicationNotFoundException(UUID id) {
        super("Application not found: " + id, HttpStatus.NOT_FOUND);
    }
}
