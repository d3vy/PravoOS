package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TimeEntryLockedException extends PravoosException {

    public TimeEntryLockedException(UUID id) {
        super("Time entry " + id + " is attached to an invoice and cannot be modified",
                HttpStatus.CONFLICT, "TIME_ENTRY_LOCKED");
    }
}
