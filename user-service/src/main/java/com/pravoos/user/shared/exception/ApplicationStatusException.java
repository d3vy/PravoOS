package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ApplicationStatusException extends PravoosException {

    public ApplicationStatusException(UUID id, Object currentStatus) {
        super("Application " + id + " cannot be reviewed, current status: " + currentStatus, HttpStatus.CONFLICT);
    }
}
