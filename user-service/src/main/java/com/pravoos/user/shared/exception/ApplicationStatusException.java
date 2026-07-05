package com.pravoos.user.shared.exception;

import com.pravoos.user.model.enums.ApplicationStatus;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ApplicationStatusException extends PravoosException {

    public ApplicationStatusException(UUID id, ApplicationStatus currentStatus) {
        super("Application " + id + " cannot be reviewed, current status: " + currentStatus, HttpStatus.CONFLICT);
    }
}
