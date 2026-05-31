package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CaseNotFoundException extends PravoosException {

    public CaseNotFoundException(UUID id) {
        super("Case not found: " + id, HttpStatus.NOT_FOUND);
    }
}
