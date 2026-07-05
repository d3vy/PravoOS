package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CaseTaskNotFoundException extends PravoosException {

    public CaseTaskNotFoundException(UUID id) {
        super("Case task not found: " + id, HttpStatus.NOT_FOUND);
    }
}
