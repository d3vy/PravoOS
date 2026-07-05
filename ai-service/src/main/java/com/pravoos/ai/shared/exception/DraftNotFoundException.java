package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DraftNotFoundException extends PravoosException {

    public DraftNotFoundException(UUID id) {
        super("Draft not found: " + id, HttpStatus.NOT_FOUND);
    }
}
