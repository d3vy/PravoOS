package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TimeEntryNotFoundException extends PravoosException {

    public TimeEntryNotFoundException(UUID id) {
        super("Time entry not found: " + id, HttpStatus.NOT_FOUND);
    }
}
